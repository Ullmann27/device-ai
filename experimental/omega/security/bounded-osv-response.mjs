/** Maximum read size is enforced on streamed bytes, not trusted Content-Length. */
export async function readBoundedOsvResponse(response) {
 const reader=response?.body?.getReader?.();
 if(!reader||typeof reader.read!=='function'||typeof reader.cancel!=='function')
   throw Error('OSV_RESPONSE_STREAM_REQUIRED');
 const chunks=[];let size=0;
 try{
   for(;;){
     const {done,value}=await reader.read();
     if(done)break;
     if(!(value instanceof Uint8Array))throw Error('OSV_RESPONSE_INVALID');
     size+=value.byteLength;
     if(size>1_000_000)throw Error('OSV_RESPONSE_TOO_LARGE');
     chunks.push(value);
   }
   try{return new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(chunks,size));}
   catch{throw Error('OSV_RESPONSE_INVALID');}
 }finally{
   try{await reader.cancel();}catch{}
   try{reader.releaseLock?.();}catch{}
 }
}
