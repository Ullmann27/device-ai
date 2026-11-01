import pathlib,json,base64,hashlib,sys
corpus=pathlib.Path(sys.argv[1]).resolve()
p=pathlib.Path(sys.argv[2]).resolve()
p.mkdir(parents=True,exist_ok=True)
raw=corpus.read_bytes()
assert hashlib.sha256(raw).hexdigest()==sys.argv[3], 'Corpus hash mismatch'
d=json.loads(raw)
def s(x): return 'b("'+base64.b64encode(x.encode()).decode()+'")'
def facts(xs):
 return 'listOf('+','.join('FrozenFact('+','.join([s(f['id']),s(f['text']),str(f['at'])+'L',s(f.get('type','USER_FACT')),s(f.get('provenance','USER_CONFIRMED')),s(f.get('trust','OWNER_CONFIRMED')),s(f.get('status','CONFIRMED')),'null' if f.get('sourceRef') is None else s(f['sourceRef']),str(f.get('updatedAt',f['at']))+'L'])+')' for f in xs)+')'
def seeds(xs):
 return 'listOf('+','.join('FrozenSeed('+s(f['input'])+','+('null' if f.get('conversation') is None else s(f['conversation']))+')' for f in xs)+')'
lines=['package at.heinz.deviceai.learning','import java.util.Base64','private fun b(s:String):String=String(Base64.getDecoder().decode(s),Charsets.UTF_8)','data class FrozenFact(val label:String,val text:String,val at:Long,val type:String,val provenance:String,val trust:String,val status:String,val sourceRef:String?,val updatedAt:Long)','data class FrozenSeed(val input:String,val conversation:String?)','data class FrozenCase(val id:String,val category:String,val facts:List<FrozenFact>,val query:String,val expected:List<String>,val seeds:List<FrozenSeed>,val seedFacts:List<FrozenFact>?,val conversation:String?)','val frozenCases=listOf(']
for c in d['cases']:
 lines.append('FrozenCase('+','.join([s(c['id']),s(c['category']),facts(c['memories']),s(c['query']),'listOf('+','.join(s(x) for x in c['expected'])+')',seeds(c.get('seeds',[])),'null' if 'seedMemories' not in c else facts(c['seedMemories']),'null' if c.get('conversation') is None else s(c['conversation'])])+'),')
lines.append(')')
(p/'FrozenDataset.kt').write_text('\n'.join(lines)+'\n')
print('Generated Kotlin dataset from verified frozen JSON: '+str(len(d['cases']))+' cases')
