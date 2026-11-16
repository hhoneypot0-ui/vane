// ==UserScript==
// @name GM Xhr Probe
// @match *://127.0.0.1:8777/gm.html*
// ==/UserScript==
(function(){
  function show(t){var d=document.getElementById('xhrout');if(!d){d=document.createElement('div');d.id='xhrout';document.body.appendChild(d);}d.textContent=t;}
  try{
    GM_xmlhttpRequest({method:'GET',url:'http://127.0.0.1:8777/article.html',
      onload:function(r){show('XHR status='+r.status+' len='+((r.responseText||'').length));},
      onerror:function(e){show('XHR error '+(e&&e.error||''));}});
  }catch(e){show('XHR throw '+e);}
})();
