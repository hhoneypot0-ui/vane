// ==UserScript==
// @name GM Probe
// @match *://127.0.0.1:8777/*
// ==/UserScript==
(function(){
  var n0 = GM_getValue('n', '0');
  var n = parseInt(n0, 10) + 1;
  GM_setValue('n', String(n));
  GM_addStyle('#gmflag{outline:3px solid red}');
  var d = document.getElementById('gmout');
  if (!d) { d = document.createElement('div'); d.id = 'gmout'; document.body.appendChild(d); }
  d.textContent = 'GM_OK run=' + GM_getValue('n', '0')
    + ' hasSet=' + (typeof GM_setValue === 'function')
    + ' hasXhr=' + (typeof GM_xmlhttpRequest === 'function')
    + ' style=' + (document.querySelectorAll('style').length);
})();
