/** Single-use, write-ahead execution claim. Persist with GitHub SHA CAS before provider use. */
export function claimReservedExecution(ledger,{runId,sequence,nowMs}={}){
 if(!ledger||!Array.isArray(ledger.reservations)||
    typeof runId!=='string'||!/^\d{1,30}$/.test(runId)||
    !Number.isSafeInteger(sequence)||sequence<0||
    !Number.isSafeInteger(nowMs)||nowMs<0)
   throw Error('EXECUTION_CLAIM_INVALID');
 const reservation=ledger.reservations[sequence];
 if(!reservation||reservation.runId!==runId||reservation.sequence!==sequence)
   throw Error('RESERVATION_NOT_FOUND');
 if(reservation.claimedAtMillis!==undefined)
   throw Error('RESERVATION_ALREADY_CLAIMED');
 if(nowMs<reservation.atMillis||nowMs-reservation.atMillis>30*60_000)
   throw Error('RESERVATION_EXPIRED');
 return {...ledger,reservations:ledger.reservations.map((r,i)=>
  i===sequence?{...r,claimedAtMillis:nowMs}:r)};
}

/** Fail closed unless an independent ledger read confirms the exact claim. */
export function verifyPersistedClaim(ledger,{runId,sequence,claimedAtMillis}={}){
 const r=ledger?.reservations?.[sequence];
 if(typeof runId!=='string'||!/^\d{1,30}$/.test(runId)||
    !Number.isSafeInteger(sequence)||sequence<0||
    !Number.isSafeInteger(claimedAtMillis)||claimedAtMillis<0||
    !r||r.runId!==runId||r.sequence!==sequence||
    r.claimedAtMillis!==claimedAtMillis)
   throw Error('GITHUB_LEDGER_CLAIM_UNVERIFIED');
 return true;
}
