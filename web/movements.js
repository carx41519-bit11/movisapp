"use strict";
let historyFilter = "all", movementDraft = null;
const movementLabel = kind => ({"stock-in":"Stock in","stock-out":"Stock out","count adjustment":"Count correction"}[kind] || kind);
function recentMovement(kind) {
  return state.history.filter(r => r.kind === kind)
    .reduce((total,r) => total + Math.abs(r.difference),0);
}
function filteredHistory() { return state.history.filter(r => historyFilter === "all" || r.kind === historyFilter); }
function movementFilters() {
  return '<div class="toolbar"><label for="movement-filter">Show movements</label><select id="movement-filter" aria-label="Filter stock movements">' +
    [["all","All movements"],["stock-in","Stock in"],["stock-out","Stock out"]].map(([v,t]) => '<option value="'+v+'" '+(historyFilter===v?'selected':'')+'>'+t+'</option>').join("") + '</select><span class="muted">Stock changes only after confirmation</span></div>';
}
function toast(message) {
  $("toast").textContent=message;$("toast").hidden=false;
  setTimeout(()=>{$("toast").hidden=true;},8000);
}
function photoBase64(file) {
  return new Promise((resolve,reject) => {
    if (!file || !["image/jpeg","image/png","image/webp"].includes(file.type)) return reject(Error("Attach a JPEG, PNG, or WebP photo of the sacks."));
    if (file.size > 6*1024*1024) return reject(Error("Use a photo smaller than 6 MB."));
    const reader=new FileReader();reader.onerror=()=>reject(Error("The photo could not be read."));
    reader.onload=()=>resolve(String(reader.result).split(",")[1]);reader.readAsDataURL(file);
  });
}
function openMovement(kind) {
  if (busy || !state) return;
  const incoming=kind==="stock-in";
  dialog(incoming?"Receive stock":"Dispatch stock",'<p class="muted">'+(incoming?'Add incoming sacks to the warehouse.':'Record sacks leaving the warehouse.')+'</p><div class="flow-steps"><span>1. Quantities + photo</span><span>2. Review balances</span><span>3. Confirm</span></div><form id="movement-form"><div class="movement-grid">'+state.products.map(p=>'<article class="movement-item"><label>'+esc(p.name)+'<small>'+esc(p.quantity)+' sacks currently available</small><input aria-label="'+esc(p.name)+' quantity" type="number" name="quantity-'+p.id+'" min="0" max="1000000" step="1" value="0" required></label></article>').join('')+'</div><label>Warehouse photo evidence<input id="movement-photo" type="file" accept="image/jpeg,image/png,image/webp" required></label><p class="muted">Use a current warehouse photo. This website flow uses your physically verified quantities, without model suggestions.</p><label>Movement reason / reference<input name="reason" maxlength="500" placeholder="e.g. Supplier delivery or dispatch reference" required></label><label><input type="checkbox" name="verified" required> I checked these quantities against the physical sacks and counted each sack once.</label><div class="form-actions"><button class="primary" type="submit">Review '+(incoming?'stock in':'stock out')+'</button></div></form>');
  let draft=null;
  $("movement-form").onsubmit=e=>{
    e.preventDefault();const form=e.currentTarget;
    run(async()=>{
      const f=new FormData(form),rows=state.products.map(p=>({product_id:p.id,quantity:Number(f.get('quantity-'+p.id))})).filter(r=>r.quantity>0);
      if (!rows.length) throw Error("Enter a quantity greater than zero for at least one product.");
      if (rows.some(r=>!Number.isInteger(r.quantity)||r.quantity>1000000)) throw Error("Use whole sack quantities between 0 and 1,000,000.");
      if (!incoming && rows.some(r=>r.quantity>state.products.find(p=>p.id===r.product_id).quantity)) throw Error("A dispatch exceeds the available stock. Reduce the quantity or refresh the inventory.");
      const file=$("movement-photo").files[0],reason=String(f.get("reason")).trim();
      if (!f.has("verified")||!reason) throw Error("Enter a reason and confirm the physical count.");
      const encoded=await photoBase64(file),signature=JSON.stringify({kind,rows,reason,file:[file.name,file.size,file.lastModified]});
      if(!draft||draft.signature!==signature)draft={signature,kind,rows,reason,encoded,openId:newRequestId(),imageId:newRequestId(),reviewId:newRequestId(),submitId:newRequestId()};
      if(!draft.aid)draft.aid=(await api("/v2/open",{kind,products:rows.map(r=>r.product_id),request_id:draft.openId})).action_id;
      await api("/v2/image",{action_id:draft.aid,image_id:draft.imageId,source:"upload",zoom:1,manual_only:true,image:encoded});
      let response=await api('/v2/state?action_id='+encodeURIComponent(draft.aid));
      if(!response.action.images.find(im=>im.id===draft.imageId)?.reviewed) {
        await api('/v2/review',{action_id:draft.aid,revision:response.action.revision,image_id:draft.imageId,checked:rows,confirmed:true,request_id:draft.reviewId});
        response=await api('/v2/state?action_id='+encodeURIComponent(draft.aid));
      }
      draft.revision=response.action.revision;draft.products=response.products;movementDraft=draft;
      showMovementConfirmation();
    });
  };
}
function showMovementConfirmation() {
  const d=movementDraft,incoming=d.kind==='stock-in';
  const rows=d.rows.map(r=>{const p=d.products.find(p=>p.id===r.product_id);return {...r,name:p.name,before:p.quantity,after:p.quantity+(incoming?r.quantity:-r.quantity)};});
  dialog('Confirm '+movementLabel(d.kind).toLowerCase(),'<p class="muted">Review the exact effect on inventory. This movement has not been submitted.</p><div class="confirmation">'+table(rows,[["Product","name"],["Available","before"],[incoming?"Receive":"Dispatch","quantity"],["After submission","after"]])+'</div><p><strong>Reference:</strong> '+esc(d.reason)+'</p><p class="muted">The server checks current balances again and prevents duplicate submissions.</p><div class="form-actions"><button id="cancel-movement" class="secondary">Leave as draft</button><button id="confirm-movement" class="primary">Confirm '+movementLabel(d.kind).toLowerCase()+'</button></div>');
  $("cancel-movement").onclick=()=>{$("detail").close();toast("Saved as a draft. Stock has not changed.");};
  $("confirm-movement").onclick=()=>run(async()=>{
    const receipt=await api('/v2/submit',{action_id:d.aid,revision:d.revision,reason:d.reason,unique_sacks:true,request_id:d.submitId});
    d.receipt=receipt;$("detail").close();movementDraft=null;page='inventory';
    toast(movementLabel(d.kind)+' saved. Inventory updated once.');
    try{await refresh();}catch(e){throw Error('Movement saved successfully. Refresh inventory to see the updated balance.');}
  });
}
$("stock-in").onclick=()=>openMovement('stock-in');
$("stock-out").onclick=()=>openMovement('stock-out');
// Draw interface icons as SVG rather than depending on a platform symbol font.
const navPaths={overview:'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',inventory:'M4 6h16v15H4zM7 3h10v3H7zM9 12h6',actions:'M7 4H4v18h16V4h-3M8 2h8v5H8zM8 11h8M8 16h8',cases:'M5 8h11a5 5 0 010 10h-5M8 4L4 8l4 4',history:'M12 3a9 9 0 110 18 9 9 0 010-18M12 7v5l4 2',reports:'M4 21V10h4v11M10 21V4h4v17M16 21V7h4v14',accounts:'M8 11a4 4 0 110-8 4 4 0 010 8M2 21v-3a6 6 0 0112 0v3M16 4a4 4 0 010 8M17 15a5 5 0 015 5',security:'M12 2l8 4v6c0 5-8 10-8 10S4 17 4 12V6zM8 12l3 3 5-6'};
document.querySelectorAll('.nav[data-page]').forEach(b=>{b.insertAdjacentHTML('afterbegin','<svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="'+navPaths[b.dataset.page]+'"/></svg>');});
