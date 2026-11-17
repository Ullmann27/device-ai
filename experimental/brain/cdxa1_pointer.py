"""CDXA1: isolated neural autoregressive source-token pointer experiment.

No pretrained weights, network, app memory, runtime hook, answer parser or output
template. Each token/UNBEKANNT/EOS is the argmax of a trained shared tanh MLP.
CSX3-style engineered lexical/binding/negation inputs are explicitly NOT learned
general semantics. Whitespace chunks form a dynamic copy vocabulary, so this
experiment cannot invent words or produce a paraphrase absent from its source.
"""
import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import time

import numpy as np

FEATURE_VERSION = "CDXA1_FEATURES_V1"
HASH_BUCKETS = 32
DIM = 31 + 3 * HASH_BUCKETS
# Reuse the literal CSX3 stop/question/negation conventions (not its weights).
STOP = set("der die das den dem des ein eine einer einen einem und oder als in im am an auf mit für von ist war hat seine seinen sie er es welche welcher welches welchen wo was wie wer".split())
NEGATIONS = {"nicht", "kein", "keine", "keinen"}
WORDS = re.compile(r"[^\W_]+(?:-[^\W_]+)*", re.UNICODE)


def words(text):
    return WORDS.findall(text)


def grams(items):
    return {w[i:i+n] for w in items for n in (3, 4) for i in range(max(0, len(w)-n+1))}


def buckets(items):
    out = np.zeros(HASH_BUCKETS)
    keys = set(items) | grams(items)
    for key in keys:
        h = 0x811c9dc5
        for c in key:
            h = ((h ^ ord(c)) * 0x01000193) & 0xffffffff
        out[(h & 0x7fffffff) % HASH_BUCKETS] += 1.0
    norm = np.linalg.norm(out)
    return out / norm if norm else out


@dataclass(frozen=True)
class Token:
    text: str
    start: int
    end: int
    sentence: int


class Input:
    """Full visible prompt and cached generic inputs; no answer argument."""
    def __init__(self, evidence, question):
        for value, limit in ((evidence, 1024), (question, 256)):
            if not isinstance(value, str) or not value.strip() or len(value.encode("utf-8")) > limit or any(ord(c)<32 or ord(c)==127 for c in value):
                raise ValueError("bounded nonempty single-line UTF-8 inputs required")
        self.evidence, self.question = evidence, question
        self.tokens = []
        sentence = 0
        for match in re.finditer(r"\S+\s*", evidence):
            self.tokens.append(Token(match[0], match.start(), match.end(), sentence))
            if re.search(r"[.;!?][\"'»”]*$", match[0].strip()): sentence += 1
        if not self.tokens or len(self.tokens)>128: raise ValueError("source token budget")
        self.sentences = []
        for s in sorted({t.sentence for t in self.tokens}):
            positions = [i for i,t in enumerate(self.tokens) if t.sentence==s]
            self.sentences.append(positions)
        self.unknown, self.eos = len(self.tokens), len(self.tokens)+1
        q_original = words(question)
        q = {w.casefold() for w in q_original if w.casefold() not in STOP}
        capital = {w.casefold() for w in q_original if w[0].isupper() and w.casefold() not in STOP}
        qg = grams(q)
        qb = buckets(q)
        self.base = np.zeros((len(self.tokens)+2, DIM))
        sentence_features = []
        for positions in self.sentences:
            text = "".join(self.tokens[i].text for i in positions)
            sw = {w.casefold() for w in words(text)}
            sg = grams(sw)
            coverage = len(q & sw)/max(1,len(q))
            binding = len(capital & sw)/max(1,len(capital))
            overlap = len(qg & sg)/(1+len(qg))
            negative = float(bool(sw & NEGATIONS))
            sentence_features.append((coverage,binding,overlap,negative))
            sb = buckets(sw-STOP)
            for j in positions:
                f = self.base[j]
                f[0] = 1.; f[3] = float(j==positions[0]); f[4] = float(j==positions[-1])
                f[5:9] = coverage,binding,overlap,negative
                f[9] = positions[0]/len(self.tokens)
                f[19] = float(j==len(self.tokens)-1)
                f[31:31+HASH_BUCKETS] = qb
                f[31+HASH_BUCKETS:31+2*HASH_BUCKETS] = sb
                f[31+2*HASH_BUCKETS:] = qb*sb
        self.base[self.unknown,1] = 1.; self.base[self.eos,2] = 1.
        global_max = np.max(np.asarray(sentence_features)[:,:3],axis=0)
        self.base[:,10:13] = global_max
        self.base[:,21] = min(len(capital),4)/4

    def features(self, previous):
        f = self.base.copy()
        if previous is None:
            f[:,13] = 1.
        elif previous==self.unknown:
            f[:,15] = 1.
        elif isinstance(previous,int) and 0<=previous<len(self.tokens):
            f[:,14] = 1.; f[:,16] = self.base[previous,4]
            f[:,18] = previous/len(self.tokens)
            for j,t in enumerate(self.tokens):
                f[j,17] = float(t.sentence==self.tokens[previous].sentence)
                distance = j-previous
                if -4<=distance<=4: f[j,22+distance+4] = 1.
        else:
            raise ValueError("invalid previous decoder pointer")
        return f


def target_pointers(x, answer):
    """TRAIN-only literal alignments. Inference never calls this function."""
    if answer=="UNBEKANNT": return [x.unknown,x.eos]
    matches = [p for p in x.sentences if "".join(x.tokens[i].text for i in p).strip()==answer.strip()]
    if len(matches)!=1: raise ValueError("TRAIN answer must be one unique literal source sentence")
    return matches[0]+[x.eos]


class PointerMLP:
    def __init__(self, seed=91, hidden=16):
        if not isinstance(hidden,int) or not 1<=hidden<=64: raise ValueError("hidden dimension")
        self.seed, self.hidden = seed, hidden
        rng = np.random.default_rng(seed)
        self.w = rng.normal(0,.08,(DIM,hidden)); self.b = np.zeros(hidden); self.v = rng.normal(0,.08,hidden)

    def parameters(self):
        return np.concatenate((self.w.ravel(),self.b,self.v))

    def load_parameters(self, parameters):
        p = np.asarray(parameters,dtype=np.float64)
        if p.shape != (DIM*self.hidden+2*self.hidden,) or not np.all(np.isfinite(p)) or np.max(np.abs(p))>1e6:
            raise ValueError("invalid parameters")
        self.w = p[:DIM*self.hidden].reshape(DIM,self.hidden).copy()
        self.b = p[DIM*self.hidden:DIM*self.hidden+self.hidden].copy(); self.v = p[-self.hidden:].copy()

    def logits(self, features):
        return np.tanh(features@self.w+self.b)@self.v

    def loss_gradient(self, features, target):
        return self.batch_gradient([(features,target)])

    def batch_gradient(self, samples):
        sizes = np.asarray([len(f) for f,t in samples]); starts = np.r_[0,np.cumsum(sizes)[:-1]]
        x = np.concatenate([f for f,t in samples]); h = np.tanh(x@self.w+self.b); scores = h@self.v
        maximum = np.maximum.reduceat(scores,starts)
        exps = np.exp(scores-np.repeat(maximum,sizes)); sums = np.add.reduceat(exps,starts)
        targets = starts+np.asarray([t for f,t in samples])
        loss = np.mean(maximum+np.log(sums)-scores[targets])
        ds = exps/np.repeat(sums,sizes); ds[targets]-=1.; ds/=len(samples)
        dv = h.T@ds; dh = ds[:,None]*self.v[None,:]*(1-h*h)
        dw = x.T@dh; db = dh.sum(axis=0)
        return float(loss),np.concatenate((dw.ravel(),db,dv))

    def generate(self, x, max_tokens=128):
        if not isinstance(max_tokens,int) or not 1<=max_tokens<=128: raise ValueError("output token budget")
        previous = None; output = []; pointers = []; ended = False
        for _ in range(max_tokens):
            token = int(np.argmax(self.logits(x.features(previous))))
            pointers.append(token)
            if token==x.eos: ended=True; break
            output.append("UNBEKANNT" if token==x.unknown else x.tokens[token].text)
            previous = token
        return {"text":"".join(output).strip(),"ended":ended,"pointers":pointers,"tokens":len(pointers),
                "stopReason":"EOS" if ended else "TOKEN_BUDGET"}

    def save(self, path, metadata):
        payload = {"schema":"CDXA1_RESEARCH_CHECKPOINT_V1","features":FEATURE_VERSION,"dim":DIM,
                   "hidden":self.hidden,"seed":self.seed,"parameters":self.parameters().tolist(),"metadata":metadata}
        with Path(path).open("x",encoding="utf-8") as out: json.dump(payload,out,separators=(",",":"),ensure_ascii=False); out.write("\n")

    @classmethod
    def restore(cls,path):
        p = json.loads(Path(path).read_text())
        if p.get("schema")!="CDXA1_RESEARCH_CHECKPOINT_V1" or p.get("features")!=FEATURE_VERSION or p.get("dim")!=DIM:
            raise ValueError("incompatible research checkpoint")
        model = cls(p["seed"],p["hidden"]); model.load_parameters(p["parameters"])
        return model


def deterministic_rule(x):
    """Separate, deliberately strong template extraction comparator. Never fallback."""
    q = x.question.casefold()
    if re.search(r"\bwo\b|stadt|wohn|lebt",q): family=0
    elif re.search(r"beruf|arbeitet",q): family=1
    elif re.search(r"trägt|kleidung|\bauf\b",q): family=2
    else: return "UNBEKANNT"
    names = {w.casefold() for w in words(x.question) if w[0].isupper() and w.casefold() not in STOP|{"stadt","beruf","kleidung","wohnort"}}
    if len(names)!=1: return "UNBEKANNT"
    name = next(iter(names)); found=[]
    for positions in x.sentences:
        text = "".join(x.tokens[i].text for i in positions).strip(); sw = {w.casefold() for w in words(text)}
        relation = bool(sw & ({"wohnt","lebt"},{"arbeitet","beruf"},{"trägt","auf"})[family])
        if name in sw and relation and not sw&NEGATIONS: found.append(text)
    return found[0] if len(found)==1 else "UNBEKANNT"


def observe(model,cases,budget):
    before=model.parameters().copy(); rows=[]
    for p in cases:
        x=Input(p["evidence"],p["question"]); g=model.generate(x,budget)
        rows.append({**p,**g,"exactCorrect":g["ended"] and g["text"]==p["answer"]})
    if not np.array_equal(before,model.parameters()): raise AssertionError("inference mutated weights")
    return rows


def digest(data): return hashlib.sha256(data).hexdigest()


def train(protocol_path,output_path,source_sha):
    if not re.fullmatch(r"[a-f0-9]{40}",source_sha): raise ValueError("pin source commit")
    out=Path(output_path); out.mkdir(exist_ok=False)
    protocol_bytes=Path(protocol_path).read_bytes(); protocol=json.loads(protocol_bytes)
    model=PointerMLP(protocol["seed"],protocol["hidden"]); samples=[]
    for p in protocol["train"]:
        x=Input(p["evidence"],p["question"]); previous=None
        for target in target_pointers(x,p["answer"]): samples.append((x.features(previous),target)); previous=target
    metadata={"sourceCommit":source_sha,"protocolSha256":digest(protocol_bytes),"features":FEATURE_VERSION,
              "dataSha256":digest(json.dumps(protocol["train"],sort_keys=True,ensure_ascii=False).encode()),
              "tokenizerSha256":digest(Path(__file__).read_bytes()),"numpy":np.__version__,"greedy":True,
              "maxOutputTokens":protocol["maxOutputTokens"],"parameterCount":len(model.parameters())}
    report={"schema":"CDXA1_NEURAL_POINTER_RESULT_V1",**metadata,"status":"FROZEN_PROTOCOL_RUNNING","stages":[],
            "ownerWeightsModified":False,"productionPromotion":False}
    rng=np.random.default_rng(protocol["seed"]); m=np.zeros_like(model.parameters()); v=m.copy(); updates=0; epoch=0
    def save_report(): (out/"report.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    for target_epoch in protocol["epochs"]:
        start=time.monotonic()
        while epoch<target_epoch:
            indices=rng.permutation(len(samples))
            for offset in range(0,len(samples),protocol["batchSize"]):
                batch=[samples[i] for i in indices[offset:offset+protocol["batchSize"]]]
                loss,gradient=model.batch_gradient(batch); updates+=1
                m=.9*m+.1*gradient; v=.999*v+.001*gradient*gradient
                model.load_parameters(model.parameters()-protocol["rate"]*(m/(1-.9**updates))/(np.sqrt(v/(1-.999**updates))+1e-8))
            epoch+=1
        checkpoint=out/f"epoch-{epoch}.cdxa1.json"
        model.save(checkpoint,{**metadata,"epochs":epoch,"updates":updates})
        restored=PointerMLP.restore(checkpoint)
        if not np.array_equal(model.parameters(),restored.parameters()): raise AssertionError("snapshot changed weights")
        stage={"epochs":epoch,"updates":updates,"checkpointSha256":digest(checkpoint.read_bytes()),
               "trainingSeconds":time.monotonic()-start,"trainLoss":sum(model.loss_gradient(f,t)[0] for f,t in samples)/len(samples),
               "train":observe(model,protocol["train"],protocol["maxOutputTokens"]),"dev":observe(model,protocol["dev"],protocol["maxOutputTokens"])}
        report["stages"].append(stage); save_report()
        core=[r for r in stage["dev"] if not r.get("outOfFamily",False)]
        print(f"epoch{epoch}: TRAIN{sum(r['exactCorrect'] for r in stage['train'])}/{len(stage['train'])} DEV-in-family{sum(r['exactCorrect'] for r in core)}/{len(core)}",flush=True)
    selected=max(report["stages"],key=lambda st:(sum(r["exactCorrect"] for r in st["dev"] if not r.get("outOfFamily",False)),-st["epochs"]))
    report["selectedEpochs"]=selected["epochs"]; report["selectedCheckpointSha256"]=selected["checkpointSha256"]
    report["deterministicRule"]=[{**p,"text":deterministic_rule(Input(p["evidence"],p["question"])),"ended":True,
                                  "exactCorrect":deterministic_rule(Input(p["evidence"],p["question"]))==p["answer"]} for p in protocol["dev"]]
    report["status"]="MEASURED_HOST_RESEARCH_ONLY"; save_report()


if __name__=="__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("protocol"); parser.add_argument("new_output"); parser.add_argument("source_sha")
    args=parser.parse_args(); train(args.protocol,args.new_output,args.source_sha)
