import dns from "node:dns/promises";
import net from "node:net";
import http from "node:http";
import https from "node:https";

const USER_AGENT = "DeviceAI-Continuum/0.4 (+https://github.com/Ullmann27/device-ai)";

/** Bind URL validation and the actual socket to the same validated DNS result. */
async function resolvePublicTarget(value) {
  const url = new URL(String(value));
  if (!["http:", "https:"].includes(url.protocol)) throw Error("Only public HTTP(S) URLs are allowed.");
  if (url.username || url.password) throw Error("Credential-bearing URLs are not allowed.");
  const name = url.hostname.replace(/^\[|\]$/g, "").toLowerCase();
  if (name === "localhost" || name.endsWith(".localhost") || name.endsWith(".local")) throw Error("Local hosts are not allowed.");
  const ipFamily = net.isIP(name);
  const addresses = ipFamily ? [{ address: name, family: ipFamily }] : await dns.lookup(name, { all: true, verbatim: true });
  if (!addresses.length || addresses.some(x => isBlockedAddress(x.address))) {
    throw Error("Private, loopback or reserved network targets are not allowed.");
  }
  return { url, address: addresses[0].address, family: addresses[0].family };
}

export async function assertPublicHttpUrl(value) {
  return (await resolvePublicTarget(value)).url;
}

export function isBlockedAddress(address) {
  if (net.isIP(address) === 4) {
    const p = address.split(".").map(Number);
    if (p[0] === 0 || p[0] === 10 || p[0] === 127) return true;
    if (p[0] === 169 && p[1] === 254) return true;
    if (p[0] === 172 && p[1] >= 16 && p[1] <= 31) return true;
    if (p[0] === 192 && (p[1] === 168 || (p[1] === 0 && p[2] === 0))) return true;
    if (p[0] === 100 && p[1] >= 64 && p[1] <= 127) return true;
    if (p[0] === 198 && (p[1] === 18 || p[1] === 19)) return true;
    if (p[0] >= 224) return true;
    return false;
  }
  if (net.isIP(address) === 6) {
    const a = address.toLowerCase();
    // Block IPv4-mapped IPv6 and transition networks rather than decoding only
    // their common dotted-quad syntax: ::ffff:7f00:1 is loopback too.
    if (a.startsWith("::ffff:") || a.startsWith("64:ff9b:") ||
        a.startsWith("2002:") || a.startsWith("2001:0:")) return true;
    return a === "::1" || a === "::" || a.startsWith("fc") || a.startsWith("fd") ||
      /^fe[89ab]/.test(a) || a.startsWith("ff") || a.startsWith("2001:db8");
  }
  return true;
}

/** Bounded HTTP(S) transfer. The custom lookup pins the verified address, so
 * DNS rebinding cannot redirect the socket after validation. TLS still checks
 * the original URL hostname and SNI; redirects must be vetted separately. */
async function requestPublic(target, { headers = {}, maxBytes = 350000, timeoutMs = 12000 } = {}) {
  const { url, address, family } = target;
  const transport = url.protocol === "https:" ? https : http;
  return new Promise((resolve, reject) => {
    const req = transport.request(url, {
      method: "GET", headers, timeout: timeoutMs, agent: false,
      lookup: (_hostname, _options, callback) => callback(null, address, family)
    }, response => {
      const status = response.statusCode ?? 0;
      const location = response.headers.location;
      if (status >= 300 && status < 400) {
        response.resume();
        resolve({ status, location, headers: response.headers, body: Buffer.alloc(0) });
        return;
      }
      const declared = Number(response.headers["content-length"]);
      if (Number.isFinite(declared) && declared > maxBytes) {
        response.destroy();
        reject(Error("Public page exceeds bounded fetch size."));
        return;
      }
      let bytes = 0;
      const chunks = [];
      response.on("data", chunk => {
        bytes += chunk.length;
        if (bytes > maxBytes) {
          response.destroy(Error("Public page exceeds bounded fetch size."));
          return;
        }
        chunks.push(chunk);
      });
      response.on("error", reject);
      response.on("end", () => resolve({
        status, location, headers: response.headers, body: Buffer.concat(chunks, bytes)
      }));
    });
    req.on("timeout", () => req.destroy(Error("Public request timed out.")));
    req.on("error", reject);
    req.end();
  });
}

export async function fetchPublicPage(value, { maxBytes = 350000, respectRobots = true } = {}) {
  if (!Number.isInteger(maxBytes) || maxBytes < 1 || maxBytes > 1000000) throw Error("Invalid maxBytes.");
  let target = await resolvePublicTarget(value);
  for (let redirects = 0; redirects <= 3; redirects++) {
    if (respectRobots && !(await robotsAllows(target.url))) throw Error("robots.txt disallows this path.");
    const response = await requestPublic(target, {
      headers: { "User-Agent": USER_AGENT, "Accept": "text/html,text/plain,application/xhtml+xml;q=0.9,*/*;q=0.2" },
      maxBytes
    });
    if (response.status >= 300 && response.status < 400 && response.location) {
      target = await resolvePublicTarget(new URL(response.location, target.url).toString());
      continue;
    }
    if (response.status < 200 || response.status >= 300) throw Error("HTTP " + response.status + " for " + target.url.hostname);
    const type = String(response.headers["content-type"] ?? "").toLowerCase();
    if (!type.includes("text/html") && !type.includes("text/plain") && !type.includes("application/xhtml+xml")) {
      throw Error("Unsupported public content type: " + type.slice(0, 80));
    }
    return extractPage(response.body.toString("utf8"), target.url.toString(), type);
  }
  throw Error("Too many redirects.");
}

export async function crawlPublicWeb({seeds,query="",maxPages=6,maxDepth=1,maxDomains=4}) {
  const queue=(seeds??[]).slice(0,6).map(url=>({url,depth:0}));
  const visited=new Set(), domains=new Set(), pages=[];
  while(queue.length&&pages.length<maxPages){
    const item=queue.shift();
    let parsed;
    try{parsed=await assertPublicHttpUrl(item.url);}catch{continue;}
    const normalized=parsed.toString();
    if(visited.has(normalized))continue;
    if(!domains.has(parsed.hostname)&&domains.size>=maxDomains)continue;
    visited.add(normalized);domains.add(parsed.hostname);
    try{
      const page=await fetchPublicPage(normalized);
      page.relevance=textRelevance(query,page.title+" "+page.text);
      if(page.text.length>=120)pages.push(page);
      if(item.depth<maxDepth){
        const ranked=page.links.map(url=>({url,score:textRelevance(query,url)}))
          .sort((a,b)=>b.score-a.score).slice(0,8);
        for(const link of ranked)queue.push({url:link.url,depth:item.depth+1});
      }
    }catch{}
  }
  return pages.sort((a,b)=>b.relevance-a.relevance).slice(0,maxPages);
}

export async function searchGitHubRepositories(query,token="",limit=4){
  const q=String(query).trim().slice(0,240);
  if(!q)return[];
  const response=await fetch("https://api.github.com/search/repositories?q="+encodeURIComponent(q)+"&sort=stars&order=desc&per_page="+limit,{
    headers:{
      "Accept":"application/vnd.github+json",
      "User-Agent":USER_AGENT,
      ...(token?{"Authorization":"Bearer "+token}:{})
    },
    signal:AbortSignal.timeout(12000)
  });
  if(!response.ok)return[];
  const data=await response.json();
  return (data.items??[]).slice(0,limit).map(x=>({
    source:"GitHub",
    title:x.full_name,
    url:x.html_url,
    text:[
      x.description??"",
      "Stars "+(x.stargazers_count??0),
      x.language?"Language "+x.language:"",
      Array.isArray(x.topics)?x.topics.join(" "):""
    ].filter(Boolean).join(". ")
  }));
}

export function extractPage(html,url,contentType="text/html"){
  const isHtml=contentType.includes("html");
  const title=isHtml ? decodeEntities((html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]??"").replace(/\s+/g," ").trim()) : "";
  let body=String(html);
  const links=[];
  if(isHtml){
    body=body.replace(/<script\b[\s\S]*?<\/script>/gi," ")
      .replace(/<style\b[\s\S]*?<\/style>/gi," ")
      .replace(/<noscript\b[\s\S]*?<\/noscript>/gi," ")
      .replace(/<(nav|header|footer|aside|form)\b[\s\S]*?<\/\1>/gi," ");
    for(const match of html.matchAll(/<a\b[^>]*href\s*=\s*["']([^"'#]+)["'][^>]*>/gi)){
      try{
        const target=new URL(decodeEntities(match[1]),url);
        if(["http:","https:"].includes(target.protocol))links.push(target.toString());
      }catch{}
      if(links.length>=80)break;
    }
    body=body.replace(/<[^>]+>/g," ");
  }
  const text=decodeEntities(body)
    .replace(/\b(skip to main content|skip to main navigation|open menu|site footer|register login|privacy statement)\b/gi," ")
    .replace(/\s+/g," ").trim().slice(0,18000);
  return {source:"PublicWeb",title:title||new URL(url).hostname,url,text,links:[...new Set(links)].slice(0,80)};
}

async function robotsAllows(url) {
  try {
    const robots = new URL("/robots.txt", url);
    const target = await resolvePublicTarget(robots);
    const response = await requestPublic(target, {
      headers: { "User-Agent": USER_AGENT }, maxBytes: 100000, timeoutMs: 5000
    });
    // Do not follow an unvalidated robots.txt redirect or silently bypass errors.
    if (response.status === 404 || response.status === 410) return true;
    if (response.status !== 200) return false;
    const text = response.body.toString("utf8");
    let active = false;
    const disallows = [];
    for (const raw of text.split(/\r?\n/)) {
      const line = raw.replace(/#.*/, "").trim();
      const [key, ...rest] = line.split(":");
      const value = rest.join(":").trim();
      if (key?.trim().toLowerCase() === "user-agent") active = value === "*";
      else if (active && key?.trim().toLowerCase() === "disallow" && value) disallows.push(value);
    }
    return !disallows.some(path => url.pathname.startsWith(path));
  } catch {
    return false;
  }
}

function textRelevance(query,text){
  const q=tokens(query),t=tokens(text);
  if(!q.size)return 0;
  let score=0;for(const x of q)if(t.has(x))score++;
  return score/q.size;
}
function tokens(text){return new Set(String(text).toLocaleLowerCase("en-US").split(/[^\p{L}\p{N}]+/u).filter(x=>x.length>=4));}
function decodeEntities(v){return String(v).replace(/&amp;/g,"&").replace(/&lt;/g,"<").replace(/&gt;/g,">").replace(/&quot;/g,'"').replace(/&#39;|&apos;/g,"'");}
