// ==UserScript==
// @name DAV Loopback Check
// @match *://127.0.0.1:8777/gm.html*
// ==/UserScript==
(function(){
  function show(t){var d=document.getElementById('davout');if(!d){d=document.createElement('div');d.id='davout';document.body.appendChild(d);}d.textContent=t;}
  try{
    GM_xmlhttpRequest({method:'PUT',url:'http://127.0.0.1:8778/vane-sync.json',data:'{"probe":1}',
      onload:function(r){show('DAV PUT status='+r.status);},
      onerror:function(e){show('DAV PUT error='+(e&&(e.error||e.status)||'unknown'));}});
  }catch(e){show('DAV throw '+e);}
})();
