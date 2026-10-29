// SPDX-License-Identifier: GPL-3.0-or-later
// PDF pages for the Sayfa reader: pdf.js draws each page on a canvas with an invisible text layer
// on top, so tapping a word works the same as in EPUBs. One page in portrait, two in landscape.
var pv = (function () {
  var doc, num = 0, page = 0, cfg, spread = 1, W, H, hl = null, t0 = null, first = true;
  var cache = {}, order = [], token = 0, dpr = window.devicePixelRatio || 1;
  var box = document.getElementById('pages');
  window.onunhandledrejection = function (e) {
    var r = e && e.reason;
    console.log('rejected: ' + (r && (r.message || r.name || r)) + ' ' + (r && r.stack));
  };
  PDFJS.workerSrc = 'pdf.worker.js';
  PDFJS.disableRange = true;
  PDFJS.disableStream = true;
  // KitKat's WebView mis-measures the fonts pdf.js rebuilds from the PDF, squeezing words together;
  // drawing the glyph outlines directly gives the same letter shapes and spacing as the printed book.
  PDFJS.disableFontFace = true;
  // Never turn font data into code: a crafted PDF font could otherwise run script (CVE-2024-4367).
  PDFJS.isEvalSupported = false;

  function key(i) { return i + '@' + W + 'x' + H + 'x' + spread; }

  /** Builds (or reuses) the element for page i, resolving when it is fully drawn. */
  function pageEl(i) {
    var k = key(i);
    if (cache[k]) return cache[k].ready;
    var el = document.createElement('div');
    el.className = 'page';
    var entry = { el: el, words: null };
    entry.ready = doc.getPage(i + 1).then(function (pg) {
      var base = pg.getViewport(1);
      var scale = Math.min((W / spread - 12) / base.width, (H - 6) / base.height);
      var vp = pg.getViewport(scale), hi = pg.getViewport(scale * dpr);
      var canvas = document.createElement('canvas');
      canvas.width = Math.floor(hi.width);
      canvas.height = Math.floor(hi.height);
      canvas.style.width = Math.floor(vp.width) + 'px';
      canvas.style.height = Math.floor(vp.height) + 'px';
      el.style.width = Math.floor(vp.width) + 'px';
      el.style.height = Math.floor(vp.height) + 'px';
      el.appendChild(canvas);
      var layer = document.createElement('div');
      layer.className = 'textLayer';
      el.appendChild(layer);
      var draw = pg.render({ canvasContext: canvas.getContext('2d'), viewport: hi });
      return (draw.promise || draw).then(function () {
        return pg.getTextContent();
      }).then(function (tc) {
        var t = PDFJS.renderTextLayer({ textContent: tc, container: layer, viewport: vp, textDivs: [] });
        return t.promise || t;
      }).then(function () { return el; });
    });
    cache[k] = entry;
    order.push(k);
    // Keep at most 8 drawn pages; never drop the ones on screen.
    var keep = [];
    while (order.length > 8) {
      var old = order.shift();
      if (cache[old] && cache[old].el.parentNode === box) keep.push(old); else delete cache[old];
    }
    order = keep.concat(order);
    return entry.ready;
  }

  function show(p) {
    if (spread === 2 && p % 2) p -= 1;
    page = Math.max(0, Math.min(num - 1, p));
    var mine = ++token, list = [page];
    if (spread === 2 && page + 1 < num) list.push(page + 1);
    Promise.all(list.map(pageEl)).then(function (els) {
      if (mine !== token) return;
      clearHl();
      while (box.firstChild) box.removeChild(box.firstChild);
      els.forEach(function (e) { box.appendChild(e); });
      if (first) { first = false; SY.onReady(page, num); makeCover(); } else SY.onPage(page, num);
      if (strip && strip.style.display !== 'none') {
        markCurrent();
        var cur = thumbs[page];  // keep the current page's preview in view while paging
        if (cur && (cur.offsetTop < strip.scrollTop || cur.offsetTop + cur.offsetHeight > strip.scrollTop + strip.clientHeight)) {
          strip.scrollTop = Math.max(0, cur.offsetTop - strip.clientHeight / 2 + cur.offsetHeight / 2);
          queueVisible();
        }
      }
      prefetch();
    }, function (err) {
      console.log('render failed: ' + (err && (err.message || err.name)) + ' ' + (err && err.stack));
      SY.onError('Sayfa çizilemedi');
    });
  }

  // Draw the pages either side ahead of time so turning is instant.
  function prefetch() {
    var want = [page + spread, page + spread + (spread - 1), page - spread];
    var i = 0;
    (function nextOne() {
      while (i < want.length && (want[i] < 0 || want[i] >= num || cache[key(want[i])])) i++;
      if (i < want.length) pageEl(want[i++]).then(nextOne, nextOne);
    })();
  }

  function makeCover() {
    if (SY.hasCover()) return;
    doc.getPage(1).then(function (pg) {
      var base = pg.getViewport(1), vp = pg.getViewport(600 / base.height);
      var c = document.createElement('canvas');
      c.width = Math.floor(vp.width); c.height = Math.floor(vp.height);
      var ctx = c.getContext('2d');
      ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, c.width, c.height);
      var r = pg.render({ canvasContext: ctx, viewport: vp });
      (r.promise || r).then(function () { SY.saveCover(c.toDataURL('image/jpeg', 0.85)); });
    });
  }

  function layout() {
    W = window.innerWidth; H = window.innerHeight;
    spread = (cfg.spread && W > H && num > 1) ? 2 : 1;
  }

  function next() { if (page + spread < num) show(page + spread); else SY.onEdge(1); }
  function prev() { if (page > 0) show(page - spread); else SY.onEdge(-1); }

  // ---- words

  function clearHl() {
    if (!hl) return;
    var p = hl.parentNode;
    if (p) {
      while (hl.firstChild) p.insertBefore(hl.firstChild, hl);
      p.removeChild(hl);
      p.normalize();
    }
    hl = null;
  }

  var WORD = /[A-Za-zÀ-ɏ'’\-]/;

  function wordAt(x, y) {
    if (!document.caretRangeFromPoint) return null;
    var r = document.caretRangeFromPoint(x, y);
    if (!r || r.startContainer.nodeType !== 3) return null;
    var node = r.startContainer, t = node.data, s = r.startOffset, e = s;
    if (s >= t.length || !WORD.test(t.charAt(s))) {
      if (s > 0 && WORD.test(t.charAt(s - 1))) s = e = s - 1; else return null;
    }
    while (s > 0 && WORD.test(t.charAt(s - 1))) s--;
    while (e < t.length && WORD.test(t.charAt(e))) e++;
    while (s < e && /['’\-]/.test(t.charAt(s))) s++;
    while (e > s && /['’\-]/.test(t.charAt(e - 1))) e--;
    if (e <= s) return null;
    var wr = document.createRange();
    wr.setStart(node, s); wr.setEnd(node, e);
    var rects = wr.getClientRects();
    for (var i = 0; i < rects.length; i++) {
      var b = rects[i];
      if (x >= b.left - 8 && x <= b.right + 8 && y >= b.top - 8 && y <= b.bottom + 8)
        return { range: wr, word: t.substring(s, e), node: node, start: s, end: e };
    }
    return null;
  }

  /**
   * A word broken at the end of a line ("hap-" / "pens") sits in two text-layer pieces.
   * Returns it rejoined as "hap-pens" (the dictionary tries it with and without the hyphen).
   */
  function joinedWord(w) {
    var div = w.node.parentNode, word = w.word, t = w.node.data;
    var after = t.substring(w.end), n = div.nextElementSibling;
    for (var k = 0; k < 3 && n && after.replace(/\s/g, '').length < 2; k++) { after += '\n' + n.textContent; n = n.nextElementSibling; }
    // The hyphen may end this piece ("influ-") or be a piece of its own ("influ" / "-" / "enced").
    var m = /^\s*\n?\s*[\-­‐‑]\s*\n\s*([a-zà-ɏ']+)/.exec(after);
    if (m) return word + '-' + m[1];
    if (!/\S/.test(t.substring(0, w.start))) {
      var before = t.substring(0, w.start), p = div.previousElementSibling;
      for (var j = 0; j < 3 && p && before.replace(/\s/g, '').length < 2; j++) { before = p.textContent + '\n' + before; p = p.previousElementSibling; }
      var m2 = /([A-Za-zÀ-ɏ']+)\s*\n?\s*[\-­‐‑]\s*\n\s*$/.exec(before);
      if (m2 && /^[a-z]/.test(word)) return m2[1] + '-' + word;
    }
    return word;
  }

  /** The sentence around the word, joined from the text-layer pieces of that page. */
  function sentenceOf(w) {
    var layer = w.node.parentNode && w.node.parentNode.parentNode;
    if (!layer || !layer.children) return '';
    // Join the pieces with "\n", mark the word with \u0001, then undo end-of-line hyphenation.
    var full = '', found = false, h = layer.clientHeight;
    for (var i = 0; i < layer.children.length; i++) {
      var d = layer.children[i], txt = d.textContent, mine = d === w.node.parentNode || d.contains(w.node);
      // Running heads and page numbers ("76 · THE GENE MACHINE") are not part of any sentence.
      if (!mine && h && (d.offsetTop < h * 0.08 || d.offsetTop > h * 0.92)) continue;
      if (mine) {
        var pre = 0, walker = document.createTreeWalker(d, 4, null, false), cur;
        while ((cur = walker.nextNode())) { if (cur === w.node) break; pre += cur.data.length; }
        txt = txt.substring(0, pre + w.start) + '\u0001' + txt.substring(pre + w.start);
        found = true;
      }
      full += (full.length ? '\n' : '') + txt;
    }
    if (!found) return '';
    // pdf.js often gives a line-end hyphen its own piece: "vehi\n-\ncle".
    full = full.replace(/\s*\n?\s*[\-­‐‑]\s*\n\s*/g, '').replace(/\s*\n\s*/g, ' ');
    var at = full.indexOf('\u0001');
    full = full.replace('\u0001', '');
    var a = at, b = at;
    while (a > 0 && !/[.!?…]/.test(full.charAt(a - 1))) a--;
    while (b < full.length && !/[.!?…]/.test(full.charAt(b))) b++;
    if (b < full.length) b++;
    var s = full.substring(a, b).replace(/\s+/g, ' ').trim();
    return s.length > 400 ? s.substring(0, 400) + '…' : s;
  }

  function tap(x, y) {
    var w = wordAt(x, y);
    if (w) {
      clearHl();
      // Read the text before highlighting: wrapping the word in a span splits its text node.
      var sentence = sentenceOf(w), word = joinedWord(w);
      try {
        hl = document.createElement('span');
        hl.className = 'sy-hl';
        hl.style.backgroundColor = cfg.hl;
        w.range.surroundContents(hl);
      } catch (err) { hl = null; }
      SY.onWord(word, sentence, y / H);
      return;
    }
    clearHl();
    SY.onTap(x / W);
  }

  document.addEventListener('touchstart', function (ev) {
    var t = ev.touches[0];
    t0 = { x: t.clientX, y: t.clientY, strip: inStrip(ev.target) };
  }, false);
  // Block native scrolling except inside the page strip, which scrolls like a normal list.
  document.addEventListener('touchmove', function (ev) { if (!(t0 && t0.strip)) ev.preventDefault(); }, false);
  document.addEventListener('touchend', function (ev) {
    if (!t0) return;
    var t = ev.changedTouches[0], dx = t.clientX - t0.x, dy = t.clientY - t0.y;
    if (t0.strip) {
      if (Math.abs(dx) < 12 && Math.abs(dy) < 12) {
        var el = ev.target;
        while (el && el !== strip && !el.getAttribute('data-i')) el = el.parentNode;
        if (el && el !== strip) show(parseInt(el.getAttribute('data-i'), 10));
      }
      t0 = null;
      return;
    }
    ev.preventDefault();
    if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) { if (dx < 0) next(); else prev(); }
    else if (Math.abs(dx) < 15 && Math.abs(dy) < 15) tap(t0.x, t0.y);
    t0 = null;
  }, false);

  // ---- page overview strip (like WPS): small previews on the left, current page outlined

  var strip = null, thumbs = [], thumbDone = {}, thumbQueue = [], thumbBusy = false, TW = 120;

  function buildStrip(ready) {
    strip = document.createElement('div');
    strip.id = 'strip';
    strip.style.cssText = 'position:fixed;left:0;width:' + (TW + 28) + 'px;overflow-y:auto;overflow-x:hidden;' +
      '-webkit-overflow-scrolling:touch;background:#1c1c1e;display:none;z-index:5;padding:10px 0;' +
      'box-shadow:2px 0 12px rgba(0,0,0,.25);';
    document.body.appendChild(strip);
    doc.getPage(1).then(function (pg) {
      var v = pg.getViewport(1), th = Math.round(TW * v.height / v.width);
      for (var i = 0; i < num; i++) {
        var t = document.createElement('div');
        t.className = 'thumb';
        t.setAttribute('data-i', i);
        t.style.cssText = 'position:relative;width:' + TW + 'px;height:' + th + 'px;margin:0 14px 14px;background:#fff;' +
          'border:3px solid transparent;border-radius:3px;box-sizing:content-box;';
        var badge = document.createElement('div');
        badge.textContent = i + 1;
        badge.style.cssText = 'position:absolute;left:0;bottom:0;min-width:22px;padding:2px 5px;font:12px sans-serif;' +
          'color:#fff;background:rgba(60,60,67,.85);text-align:center;z-index:1;';
        t.appendChild(badge);
        t.badge = badge;
        strip.appendChild(t);
        thumbs.push(t);
      }
      ready();
    });
    strip.addEventListener('scroll', function () { queueVisible(); }, false);
  }

  function markCurrent() {
    for (var i = 0; i < thumbs.length; i++) {
      var on = i === page || (spread === 2 && i === page + 1);
      thumbs[i].style.borderColor = on ? '#FF9F0A' : 'transparent';
      thumbs[i].badge.style.background = on ? '#FF9F0A' : 'rgba(60,60,67,.85)';
    }
  }

  function queueVisible() {
    if (!strip || strip.style.display === 'none') return;
    var top = strip.scrollTop - 400, bottom = strip.scrollTop + strip.clientHeight + 400;
    for (var i = 0; i < thumbs.length; i++) {
      var t = thumbs[i];
      if (t.offsetTop + t.offsetHeight >= top && t.offsetTop <= bottom && !thumbDone[i]) {
        thumbDone[i] = true;
        thumbQueue.push(i);
      }
    }
    drawNextThumb();
  }

  // One preview at a time, so the strip never blocks page turns.
  function drawNextThumb() {
    if (thumbBusy || !thumbQueue.length) return;
    thumbBusy = true;
    var i = thumbQueue.shift();
    doc.getPage(i + 1).then(function (pg) {
      var v = pg.getViewport(1), vp = pg.getViewport(TW * dpr / v.width);
      var c = document.createElement('canvas');
      c.width = Math.floor(vp.width); c.height = Math.floor(vp.height);
      c.style.cssText = 'width:100%;height:100%;display:block;';
      var r = pg.render({ canvasContext: c.getContext('2d'), viewport: vp });
      return (r.promise || r).then(function () { thumbs[i].insertBefore(c, thumbs[i].firstChild); });
    }).then(function () { thumbBusy = false; drawNextThumb(); }, function () { thumbBusy = false; drawNextThumb(); });
  }

  function showStrip(show, top, bottom) {
    if (!doc) return;
    function place() {
      markCurrent();
      var t = thumbs[page];
      if (t) strip.scrollTop = Math.max(0, t.offsetTop - strip.clientHeight / 2 + t.offsetHeight / 2);
      queueVisible();
    }
    var fresh = !strip;
    if (fresh) buildStrip(function () { if (strip.style.display !== 'none') place(); });
    strip.style.top = top + 'px';
    strip.style.bottom = bottom + 'px';
    strip.style.display = show ? 'block' : 'none';
    if (show && !fresh) place();
  }

  function inStrip(el) {
    while (el) { if (el === strip) return true; el = el.parentNode; }
    return false;
  }

  // Re-draw at the new size when the reading area changes (system bars hiding, rotation).
  var resizeTimer = null;
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      if (doc && (window.innerWidth !== W || window.innerHeight !== H)) { layout(); show(page); }
    }, 150);
  }, false);

  // ---- table of contents

  function outline() {
    doc.getOutline().then(function (items) {
      var out = [], jobs = [];
      (function walk(list, depth) {
        (list || []).forEach(function (it) {
          var entry = { title: (depth ? ' ' : '') + it.title, page: -1 };
          out.push(entry);
          var dest = it.dest;
          jobs.push((typeof dest === 'string' ? doc.getDestination(dest) : Promise.resolve(dest)).then(function (d) {
            if (d && d[0]) return doc.getPageIndex(d[0]).then(function (n) { entry.page = n; });
          }).catch(function () {}));
          if (depth < 1) walk(it.items, depth + 1);
        });
      })(items, 0);
      Promise.all(jobs).then(function () {
        SY.onToc(JSON.stringify(out.filter(function (e) { return e.page >= 0; })));
      });
    }, function () { SY.onToc('[]'); });
  }

  return {
    init: function (c) {
      cfg = c;
      document.body.style.background = c.bg;
      document.body.className = c.dark ? 'dark' : '';
      var task = PDFJS.getDocument({ url: c.url });
      (task.promise || task).then(function (d) {
        doc = d;
        num = d.numPages;
        layout();
        show(c.page || 0);
      }, function (err) {
        console.log('getDocument failed: ' + (err && (err.message || err.name)) + ' ' + err);
        SY.onError('PDF açılamadı');
      });
    },
    style: function (c) {
      cfg.bg = c.bg; cfg.hl = c.hl; cfg.spread = c.spread;
      document.body.style.background = c.bg;
      document.body.className = c.dark ? 'dark' : '';
      if (doc) { layout(); show(page); }
    },
    relayout: function () { if (doc) { layout(); show(page); } },
    next: next, prev: prev, clearHl: clearHl, outline: outline, strip: showStrip,
    go: function (p) { if (doc) show(p); }
  };
})();
var sy = { clearHl: function () { pv.clearHl(); } };
