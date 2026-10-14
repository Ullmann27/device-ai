import dns from "node:dns/promises";
import net from "node:net";

const USER_AGENT = "DeviceAI-Continuum/0.4 (+https://github.com/Ullmann27/device-ai)";

export async function assertPublicHttpUrl(value) {
  const url = new URL(String(value));
  if (!["http:","https:"].includes(url.protocol)) throw new Error("Only public HTTP(S) URLs are allowed.");
  if (url.username || url.password) throw new Error("Credential-bearing URLs are not allowed.");
  const host = url.hostname.toLocaleLowerCase("en-US");
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
    throw new Error("Local hosts are not allowed.");
  }
  const addresses = net.isIP(host)
    ? [{address:host,family:net.isIP(host)}]
    : await dns.lookup(host,{all:true,verbatim:true});
  if (!addresses.length || addresses.some(x=>isBlockedAddress(x.address))) {
    throw new Error("Private, loopback or reserved network targets are not allowed.");
  }
  return url;
}

export function isBlockedAddress(address) {
  if (net.isIP(address) === 4) {
    const p=address.split(".").map(Number);
    if(p[0]===10||p[0]===127||p[0]===0)return true;
    if(p[0]===169&&p[1]===254)return true;
    if(p[0]===172&&p[1]>=16&&p[1]<=31)return true;
    if(p[0]===192&&p[1]===168)return true;
    if(p[0]===100&&p[1]>=64&&p[1]<=127)return true;
    if(p[0]>=224)return true;
    if(p[0]===198&&(p[1]===18||p[1]===19))return true;
    if(p[0]===192&&p[1]===0&&p[2]===0)return true;
    return false;
  }
  if (net.isIP(address) === 6) {
    const a=address.toLocaleLowerCase("en-US");
    return a==="::1"||a==="::"||a.startsWith("fc")||a.startsWith("fd")||
      /^fe[89ab]/.test(a)||a.startsWith("ff")||a.startsWith("2001:db8");
  }
  return true;
}

export async function fetchPublicPage(value, {maxBytes=350000, respectRobots=true}={}) {
  let url=await assertPublicHttpUrl(value);
  if(respectRobots && !(await robotsAllows(url)))throw new Error("robots.txt disallows this path.");
  for(let redirects=0;redirects<=3;redirects++){
    const response=await fetch(url,{
      redirect:"manual",
      headers:{"User-Agent":USER_AGENT,"Accept":"text/html,text/plain,application/xhtml+xml;q=0.9,*/*;q=0.2"},
      signal:AbortSignal.timeout(12000)
    });
    if(response.status>=300&&response.status<400&&response.headers.get("location")){
      url=await assertPublicHttpUrl(new URL(response.headers.get("location"),url).toString());
      continue;
    }
    if(!response.ok)throw new Error("HTTP "+response.status+" for "+url.hostname);
    const type=(response.headers.get("content-type")??"").toLocaleLowerCase("en-US");
    if(!type.includes("text/html")&&!type.includes("text/plain")&&!type.includes("application/xhtml+xml")){
      throw new Error("Unsupported public content type: "+type.slice(0,80));
    }
    const buf=Buffer.from(await response.arrayBuffer());
    if(buf.length>maxBytes)throw new Error("Public page exceeds bounded fetch size.");
    return extractPage(buf.toString("utf8"),url.toString(),type);
  }
  throw new Error("Too many redirects.");
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

async function robotsAllows(url){
  try{
    const robots=new URL("/robots.txt",url);
    const response=await fetch(robots,{headers:{"User-Agent":USER_AGENT},signal:AbortSignal.timeout(5000)});
    if(!response.ok)return true;
    const text=(await response.text()).slice(0,100000);
    let active=false;
    const disallows=[];
    for(const raw of text.split(/\r?\n/)){
      const line=raw.replace(/#.*/,"").trim();
      const [key,...rest]=line.split(":");
      const value=rest.join(":").trim();
      if(key?.trim().toLocaleLowerCase("en-US")==="user-agent")active=value==="*";
      else if(active&&key?.trim().toLocaleLowerCase("en-US")==="disallow"&&value)disallows.push(value);
    }
    return !disallows.some(path=>url.pathname.startsWith(path));
  }catch{return true;}
}

function textRelevance(query,text){
  const q=tokens(query),t=tokens(text);
  if(!q.size)return 0;
  let score=0;for(const x of q)if(t.has(x))score++;
  return score/q.size;
}
function tokens(text){return new Set(String(text).toLocaleLowerCase("en-US").split(/[^\p{L}\p{N}]+/u).filter(x=>x.length>=4));}
function decodeEntities(v){return String(v).replace(/&amp;/g,"&").replace(/&lt;/g,"<").replace(/&gt;/g,">").replace(/&quot;/g,'"').replace(/&#39;|&apos;/g,"'");}
