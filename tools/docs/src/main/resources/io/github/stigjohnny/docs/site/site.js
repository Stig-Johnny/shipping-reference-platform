/*
 * The site's one script, generated with the site by tools/docs: the theme switch, the navigation disclosure, the copy
 * button of the brain, diagram enlargement, and Mermaid on the diagram pages, whose drawings it fits to their bounds.
 * It stores nothing, fetches nothing and writes no markup. Without it every page still works: the navigation stays
 * open and the diagrams show their source.
 */
(function () {
  'use strict';

  var root = document.documentElement;

  // Theme: the system's preference, unless the reader chooses Light or Dark; the choice lasts until the page reloads.
  var themeSwitch = document.querySelector('.theme-switch');
  if (themeSwitch) {
    themeSwitch.hidden = false;
    themeSwitch.addEventListener('change', function (event) {
      var choice = event.target && event.target.value;
      if (choice === 'light' || choice === 'dark') {
        root.setAttribute('data-theme', choice);
      } else {
        root.removeAttribute('data-theme');
      }
    });
  }

  // Navigation: a labelled disclosure below 720px, always open from 720px up. When the breakpoint changes, focus stays
  // usable, whether or not the disclosure was already open: a focused navigation that collapses hands focus to its
  // summary, and a focused summary that disappears at desktop width hands it to the current page's link, or the first.
  // The browser can move focus to <body> as soon as the focused element is hidden, before the media query's change
  // event runs, so the script remembers where focus was last; a pointer press outside the navigation forgets it.
  var disclosure = document.querySelector('.site-nav-disclosure');
  if (disclosure && window.matchMedia) {
    var wide = window.matchMedia('(min-width: 720px)');
    var summary = disclosure.querySelector('summary');
    var lastInside = false;
    var lastSummary = false;
    document.addEventListener('focusin', function (event) {
      lastInside = disclosure.contains(event.target);
      lastSummary = summary !== null && event.target === summary;
    });
    document.addEventListener('pointerdown', function (event) {
      if (!disclosure.contains(event.target)) {
        lastInside = false;
        lastSummary = false;
      }
    });
    var syncNavigation = function () {
      var active = document.activeElement;
      var lost = !active || active === document.body;
      var focusInside = disclosure.contains(active) || (lost && lastInside);
      var summaryFocused = (summary !== null && active === summary) || (lost && lastSummary);
      var opening = wide.matches;
      disclosure.open = opening;
      if (!opening && focusInside && summary) {
        summary.focus();
      } else if (opening && summaryFocused) {
        var target = disclosure.querySelector('a[aria-current="page"]') || disclosure.querySelector('a');
        if (target) {
          target.focus();
        }
      }
    };
    syncNavigation();
    if (wide.addEventListener) {
      wide.addEventListener('change', syncNavigation);
    }
  }

  // Copy the agent context; the outcome is announced in words in a status region.
  var copyButton = document.querySelector('[data-copy-target]');
  if (copyButton) {
    var copySource = document.getElementById(copyButton.getAttribute('data-copy-target'));
    var copyStatus = document.getElementById(copyButton.getAttribute('data-status'));
    var report = function (message) {
      if (copyStatus) {
        copyStatus.textContent = message;
      }
    };
    copyButton.hidden = false;
    copyButton.addEventListener('click', function () {
      if (!copySource || !navigator.clipboard || !window.isSecureContext) {
        report('Copying is not available here. Open "Agent context (BRAIN.md)" below and copy the text by hand.');
        return;
      }
      navigator.clipboard.writeText(copySource.textContent).then(function () {
        report('Agent context copied to the clipboard.');
      }, function () {
        report('Copying failed. Open "Agent context (BRAIN.md)" below and copy the text by hand.');
      });
    });
  }

  // Diagram enlargement: a toggle button (aria-pressed); the enlarged diagram scrolls inside its labelled region. The
  // stylesheet widens the drawing to its natural width or one and a half times the region, whichever is wider, within
  // the same fitted bounds.
  // The button stays hidden until its figure's drawing is fitted (revealEnlarge, below); a figure whose drawing
  // failed never offers it.
  Array.prototype.forEach.call(document.querySelectorAll('[data-enlarge]'), function (button) {
    var figure = document.getElementById(button.getAttribute('data-enlarge'));
    if (!figure) {
      return;
    }
    button.addEventListener('click', function () {
      var enlarged = button.getAttribute('aria-pressed') !== 'true';
      button.setAttribute('aria-pressed', enlarged ? 'true' : 'false');
      figure.classList.toggle('is-enlarged', enlarged);
    });
  });

  // Diagram fitting. Mermaid sizes a drawing from a box that leaves out strokes and arrowheads, so a drawing could be
  // cut at its edges. Once drawn, its viewBox becomes its complete bounds, measured in the drawing's own units: every
  // shape, label and edge with half its stroke, and the arrowhead at each marked end, plus padding. The drawing keeps
  // that aspect ratio at every size, fitted to its region or enlarged. Until then it is invisible: the stylesheet hides
  // the drawing of a figure without data-fitted="true", while the caption and the tables stay, and without the script
  // there is no drawing, only its source. A ResizeObserver on each region fits again when the region's width changes;
  // bounds that changed are applied in the next frame, with the drawing hidden until then, so no frame shows it cut.
  var FIT_PADDING = 16;
  var MARKER_REFERENCE = /#([^"')\s]+)["']?\)/;
  var drawnBounds = function (svg) {
    var screen = svg.getScreenCTM();
    if (!screen || !screen.a) {
      return null;
    }
    var toUser = screen.inverse();
    var bounds = null;
    var include = function (matrix, x, y) {
      var point = new DOMPoint(x, y).matrixTransform(matrix);
      if (bounds === null) {
        bounds = { left: point.x, top: point.y, right: point.x, bottom: point.y };
        return;
      }
      bounds.left = Math.min(bounds.left, point.x);
      bounds.top = Math.min(bounds.top, point.y);
      bounds.right = Math.max(bounds.right, point.x);
      bounds.bottom = Math.max(bounds.bottom, point.y);
    };
    var includeBox = function (matrix, left, top, right, bottom) {
      include(matrix, left, top);
      include(matrix, right, top);
      include(matrix, left, bottom);
      include(matrix, right, bottom);
    };
    var shapes = svg.querySelectorAll('path, rect, circle, ellipse, line, polyline, polygon, text, image');
    Array.prototype.forEach.call(shapes, function (shape) {
      if (shape.closest('defs, marker, clipPath, mask, pattern, symbol')) {
        return;
      }
      var local = shape.getScreenCTM();
      var box = shape.getBBox();
      if (!local || (!box.width && !box.height)) {
        return;
      }
      var matrix = toUser.multiply(local);
      var style = window.getComputedStyle(shape);
      var stroke = style.stroke && style.stroke !== 'none' ? (parseFloat(style.strokeWidth) || 0) / 2 : 0;
      includeBox(matrix, box.x - stroke, box.y - stroke, box.x + box.width + stroke, box.y + box.height + stroke);
      if (typeof shape.getTotalLength !== 'function') {
        return;
      }
      ['marker-start', 'marker-end'].forEach(function (property) {
        var reference = MARKER_REFERENCE.exec(shape.getAttribute(property) || style.getPropertyValue(property) || '');
        var marker = reference && document.getElementById(reference[1]);
        if (!marker) {
          return;
        }
        var size = Math.max(parseFloat(marker.getAttribute('markerWidth')) || 3,
            parseFloat(marker.getAttribute('markerHeight')) || 3);
        if (marker.getAttribute('markerUnits') !== 'userSpaceOnUse') {
          size *= parseFloat(style.strokeWidth) || 1;
        }
        var end = shape.getPointAtLength(property === 'marker-end' ? shape.getTotalLength() : 0);
        includeBox(matrix, end.x - size, end.y - size, end.x + size, end.y + size);
      });
    });
    return bounds;
  };
  var measureFit = function (figure) {
    var svg = figure.querySelector('.diagram-scroll svg');
    var bounds = null;
    try {
      bounds = svg ? drawnBounds(svg) : null;
    } catch (error) {
      bounds = null;
    }
    if (!bounds || bounds.right <= bounds.left || bounds.bottom <= bounds.top) {
      return null;
    }
    var left = Math.floor(bounds.left) - FIT_PADDING;
    var top = Math.floor(bounds.top) - FIT_PADDING;
    var width = Math.ceil(bounds.right) + FIT_PADDING - left;
    var height = Math.ceil(bounds.bottom) + FIT_PADDING - top;
    return { svg: svg, viewBox: left + ' ' + top + ' ' + width + ' ' + height, width: width, height: height };
  };
  var applyFit = function (figure, fit) {
    var svg = fit.svg;
    svg.setAttribute('viewBox', fit.viewBox);
    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
    svg.setAttribute('width', String(fit.width));
    svg.setAttribute('height', String(fit.height));
    svg.style.removeProperty('max-width');
    svg.style.setProperty('--diagram-width', fit.width + 'px');
    figure.setAttribute('data-fitted', 'true');
  };
  var fitOnResize = function (figure) {
    var region = figure.querySelector('.diagram-scroll');
    if (!region || !window.ResizeObserver) {
      return;
    }
    var lastWidth = -1;
    var observer = new window.ResizeObserver(function () {
      var width = region.clientWidth;
      if (width === 0 || width === lastWidth) {
        return;
      }
      lastWidth = width;
      var fit = measureFit(figure);
      if (!fit || (figure.getAttribute('data-fitted') === 'true' && fit.svg.getAttribute('viewBox') === fit.viewBox)) {
        return;
      }
      figure.removeAttribute('data-fitted');
      window.requestAnimationFrame(function () {
        var next = measureFit(figure);
        if (next) {
          applyFit(figure, next);
        }
      });
    });
    observer.observe(region);
  };

  var revealEnlarge = function (figure) {
    var button = figure.querySelector('[data-enlarge]');
    if (button) {
      button.hidden = false;
    }
  };
  // A figure whose drawing could not be made or fitted shows its failure note and its source again (as text), and
  // offers no enlargement.
  var failFigure = function (figure, source, original) {
    figure.removeAttribute('data-fitted');
    figure.classList.remove('is-enlarged');
    if (source.textContent !== original || source.querySelector('svg')) {
      source.textContent = original;
    }
    source.removeAttribute('data-processed');
    var button = figure.querySelector('[data-enlarge]');
    if (button) {
      button.hidden = true;
      button.setAttribute('aria-pressed', 'false');
    }
    var note = figure.querySelector('.diagram-failed');
    if (note) {
      note.hidden = false;
    }
  };

  // Mermaid, on the diagram pages only: initialised once, strict, without HTML labels, and run only on the diagram
  // sources the generator emitted. Each drawing is then fitted, and only then can it be enlarged. A missing or
  // incomplete Mermaid, an exception, a rejected rendering or a drawing that cannot be fitted leaves the source and
  // shows the note, which says where the text equivalent is.
  var diagrams = document.querySelectorAll('pre.diagram-source[data-diagram]');
  var originals = Array.prototype.map.call(diagrams, function (source) {
    return source.textContent;
  });
  var failAll = function () {
    Array.prototype.forEach.call(diagrams, function (source, index) {
      var figure = source.closest('figure.diagram');
      if (figure) {
        failFigure(figure, source, originals[index]);
      }
    });
  };
  var mermaid = window.mermaid;
  if (diagrams.length > 0 && !(mermaid && typeof mermaid.initialize === 'function'
      && typeof mermaid.run === 'function')) {
    failAll();
  } else if (diagrams.length > 0) {
    try {
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      htmlLabels: false,
      // Reserve room for boundary titles before routing relationships around their contents.
      flowchart: { htmlLabels: false, useMaxWidth: true, subGraphTitleMargin: { top: 16, bottom: 32 } },
      theme: 'base',
      // Mermaid's default SVG label backing is translucent; arrows must not show through relationship text.
      themeCSS: '.edgeLabel .label rect { fill: #FFFDF7; opacity: 1; }',
      fontFamily: 'system-ui, sans-serif',
      // The drawing sheet of docs/ui/documentation-site.md: ink lines on the light sheet in both themes. Node colours
      // come from the generated definitions and are not themed.
      themeVariables: {
        fontFamily: 'system-ui, sans-serif', fontSize: '18px',
        background: '#FFFDF7', edgeLabelBackground: '#FFFDF7',
        primaryColor: '#F4F1E8', primaryTextColor: '#172B3A',
        primaryBorderColor: '#687987', textColor: '#172B3A',
        lineColor: '#172B3A', clusterBkg: '#FFFDF7',
        clusterBorder: '#687987'
      }
    });
    mermaid.run({ nodes: Array.prototype.slice.call(diagrams) }).then(function () {
      Array.prototype.forEach.call(diagrams, function (source, index) {
        var figure = source.closest('figure.diagram');
        if (!figure) {
          return;
        }
        var fit = measureFit(figure);
        if (fit) {
          applyFit(figure, fit);
          revealEnlarge(figure);
          fitOnResize(figure);
        } else {
          failFigure(figure, source, originals[index]);
        }
      });
    }, failAll);
    } catch (error) {
      failAll();
    }
  }
})();

// Search uses only text already present in the generated HTML. It does not fetch an index or parse query markup.
(function () {
  'use strict';
  var input = document.getElementById('site-search');
  if (!input) return;
  var list = document.getElementById('search-results');
  var status = document.getElementById('search-status');
  var rows = Array.from(list.children).map(function (row, order) {
    return { row: row, order: order, fields: [row.querySelector('.search-title'),
      row.querySelector('.search-summary'), row.querySelector('.search-headings')] };
  });
  document.querySelector('.search-tools').hidden = false;
  function filter() {
    var query = input.value.trim().toLowerCase();
    var matches = rows.map(function (entry) {
      var field = query ? entry.fields.findIndex(function (node) {
        return node.textContent.toLowerCase().includes(query);
      }) : 0;
      entry.row.hidden = field < 0;
      var note = entry.row.querySelector('.search-match');
      note.hidden = !query || field < 0;
      note.textContent = field < 0 ? '' : 'Matched ' + ['title', 'summary', 'heading'][field] + ': '
        + (field === 2 ? Array.from(entry.fields[2].children).find(function (heading) {
          return heading.textContent.toLowerCase().includes(query);
        }).textContent : entry.fields[field].textContent);
      return { entry: entry, rank: field < 0 ? 3 : field };
    });
    matches.sort(function (a, b) { return a.rank - b.rank || a.entry.order - b.entry.order; });
    matches.forEach(function (match) { list.append(match.entry.row); });
    var count = rows.filter(function (entry) { return !entry.row.hidden; }).length;
    status.textContent = count + (count === 1 ? ' page' : ' pages')
      + (query ? (count === 1 ? ' matches.' : ' match.') + ' Title matches first.' : ' available.')
      + (count ? '' : ' Try a shorter phrase.');
  }
  input.addEventListener('input', filter);
  input.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') { input.value = ''; filter(); }
    if (event.key === 'Enter') {
      event.preventDefault();
      var first = list.querySelector('li:not([hidden]) a');
      if (first) first.focus();
    }
  });
  filter();
})();

// Progressive enhancement of the Brain's typed HTML records. Text and relationships have one source of truth.
(function () {
  'use strict';
  var graph = document.getElementById('brain-graph');
  if (!graph) return;
  var byId = function (id) { return document.getElementById(id); };
  var records = byId('brain-records');
  var nodes = new Map(Array.from(records.querySelectorAll('[data-brain-node]')).map(function (record) {
    return [record.dataset.brainNode, { id: record.dataset.brainNode, kind: record.dataset.kind,
      state: record.dataset.state, name: record.querySelector('[data-brain-title]').textContent,
      description: record.querySelector('.brain-description').textContent, record: record }];
  }));
  var edges = Array.from(records.querySelectorAll('[data-relation]')).map(function (edge) {
    return { from: edge.dataset.from, to: edge.dataset.to, kind: edge.dataset.relation,
      meaning: edge.querySelector('.brain-meaning').textContent };
  });
  var selected = nodes.has('quotes') ? 'quotes' : nodes.keys().next().value;
  var view = 'modules';
  var moduleSelection = selected;
  var firstControl = Array.from(nodes.values()).find(function (node) { return node.kind === 'control'; });
  var controlSelection = firstControl ? firstControl.id : selected;
  var input = byId('brain-find');
  var results = byId('brain-results');
  var modulePositions = { quotes: [220, 210], shipments: [720, 235], tenants: [475, 80],
    audit: [270, 485], orders: [760, 480], tracking: [535, 565], returns: [820, 90] };
  function element(tag, text) {
    var result = document.createElement(tag);
    if (text) result.textContent = text;
    return result;
  }
  function svg(tag, attributes, text) {
    var result = document.createElementNS('http://www.w3.org/2000/svg', tag);
    Object.entries(attributes).forEach(function (pair) { result.setAttribute(pair[0], String(pair[1])); });
    if (text) result.textContent = text;
    return result;
  }
  function owner(id) {
    if (nodes.get(id).kind === 'module') return id;
    var edge = edges.find(function (entry) { return entry.kind === 'ownership' && entry.to === id; });
    return edge ? edge.from : null;
  }
  function select(id, focus) {
    if (!nodes.has(id)) return;
    var previousSelected = selected;
    selected = id;
    var node = nodes.get(id);
    if (node.kind === 'module' || node.kind === 'capability') {
      moduleSelection = owner(id);
      var moduleContext = view === 'journey' || (node.kind === 'module' && view === 'decisions');
      var evidenceContext = view === 'controls' && controlSelection === id;
      if (!moduleContext && !evidenceContext) showView('modules');
    } else if (node.kind === 'control') {
      controlSelection = id;
      showView('controls');
    } else {
      var relation = node.kind === 'decision' ? 'decision' : 'evidence';
      var previous = node.kind === 'decision' ? moduleSelection : controlSelection;
      var parents = edges.filter(function (edge) { return edge.to === id && edge.kind === relation; });
      var parent = parents.find(function (edge) { return edge.from === previousSelected; })
        || parents.find(function (edge) { return edge.from === previous; }) || parents[0];
      if (parent) {
        if (node.kind === 'decision') moduleSelection = parent.from;
        else controlSelection = parent.from;
        showView(node.kind === 'decision' ? 'decisions' : 'controls');
      }
    }
    var clone = node.record.cloneNode(true);
    clone.removeAttribute('id');
    clone.removeAttribute('data-brain-node');
    var heading = clone.querySelector('h3');
    heading.setAttribute('tabindex', '-1');
    byId('brain-detail').replaceChildren(clone);
    clone.querySelectorAll('a[href^="#brain-node-"]').forEach(function (link) {
      link.addEventListener('click', function (event) {
        event.preventDefault();
        select(link.getAttribute('href').slice('#brain-node-'.length), true);
      });
    });
    draw();
    filter();
    if (focus) heading.focus();
  }
  function draw() {
    graph.replaceChildren();
    var positions = new Map();
    var visibleEdges;
    var caption;
    if (view === 'modules' || view === 'journey') {
      var modules = Array.from(nodes.values()).filter(function (node) { return node.kind === 'module'; });
      modules.forEach(function (node, index) {
        var position = modulePositions[node.id] || [500 + Math.cos(index) * 300, 320 + Math.sin(index) * 210];
        positions.set(node.id, position);
        var capabilities = edges.filter(function (edge) { return edge.from === node.id && edge.kind === 'ownership'; });
        capabilities.forEach(function (edge, at) {
          var angle = at / capabilities.length * Math.PI * 2;
          positions.set(edge.to, [position[0] + Math.cos(angle) * 63, position[1] + Math.sin(angle) * 63]);
        });
      });
      visibleEdges = edges.filter(function (edge) { return edge.kind === 'dependency' || edge.kind === 'ownership'; });
      caption = modules.length + ' API modules · Arrows: declared code dependencies. Select a module for modes and sources.';
    } else {
      var centre = view === 'decisions' ? moduleSelection : controlSelection;
      positions.set(centre, [500, 325]);
      visibleEdges = edges.filter(function (edge) {
        return edge.from === centre && edge.kind === (view === 'decisions' ? 'decision' : 'evidence');
      });
      visibleEdges.forEach(function (edge, index) {
        var angle = index / visibleEdges.length * Math.PI * 2 - Math.PI / 2;
        positions.set(edge.to, [500 + Math.cos(angle) * 315, 325 + Math.sin(angle) * 235]);
      });
      caption = nodes.get(centre).name + (view === 'decisions' ? ' · Arrows: specification names decision.'
        : ' · Arrows: evidenced by passing test in this build.') + (visibleEdges.length ? '' : ' No such relations recorded.');
    }
    byId('brain-map-caption').textContent = caption;
    var defs = svg('defs', {});
    var marker = svg('marker', { id: 'brain-arrow', viewBox: '0 0 10 10', refX: 9, refY: 5,
      markerWidth: 5, markerHeight: 5, orient: 'auto-start-reverse' });
    marker.append(svg('path', { d: 'M 0 0 L 10 5 L 0 10 z', fill: '#bba0dc' }));
    defs.append(marker); graph.append(defs);
    visibleEdges.forEach(function (edge) {
      var from = positions.get(edge.from), to = positions.get(edge.to);
      if (!from || !to) return;
      var distance = Math.hypot(to[0] - from[0], to[1] - from[1]);
      var dx = (to[0] - from[0]) / distance, dy = (to[1] - from[1]) / distance;
      var line = svg('path', { d: 'M ' + (from[0] + dx * 25) + ' ' + (from[1] + dy * 25)
        + ' L ' + (to[0] - dx * 26) + ' ' + (to[1] - dy * 26),
        'class': 'brain-edge ' + edge.kind + (edge.from === selected || edge.to === selected ? ' connected' : '') });
      if (edge.kind !== 'ownership') line.setAttribute('marker-end', 'url(#brain-arrow)');
      line.append(svg('title', {}, nodes.get(edge.from).name + ' → ' + nodes.get(edge.to).name + ': ' + edge.meaning));
      graph.append(line);
    });
    positions.forEach(function (position, id) {
      var node = nodes.get(id), small = node.kind === 'capability' && (view === 'modules' || view === 'journey');
      var group = svg('g', { 'class': 'brain-node ' + node.kind + ' ' + node.state + (small ? ' satellite' : '') + (id === selected ? ' selected' : ''),
        transform: 'translate(' + position[0] + ' ' + position[1] + ')', tabindex: 0, role: 'button',
        'aria-label': node.name + ', ' + node.kind + ', ' + node.state, 'aria-pressed': String(id === selected) });
      group.append(svg('circle', { r: small ? 22 : 34, 'class': 'brain-hit' }),
        svg('circle', { r: small ? 10 : 31, 'class': 'brain-halo' }),
        svg('circle', { r: small ? 4 : 11, 'class': 'brain-core' }), svg('title', {}, node.name + ': ' + node.state));
      if (!small) {
        var name = node.name.replace(/^control\./, '');
        if (name.length > 17) name = name.slice(0, 15) + '…';
        group.append(svg('text', { x: 0, y: -33, 'text-anchor': 'middle' }, name),
          svg('text', { x: 0, y: -14, 'text-anchor': 'middle', 'class': 'brain-node-status' }, node.kind + ': ' + node.state));
      }
      group.addEventListener('click', function () { select(id, true); });
      group.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(id, true); }
      });
      graph.append(group);
    });
  }
  function filter() {
    var query = input.value.trim().toLowerCase();
    var found = Array.from(nodes.values()).filter(function (node) {
      return query ? (node.name + ' ' + node.description).toLowerCase().includes(query)
        : node.kind === (view === 'controls' ? 'control' : 'module');
    });
    if (query) found.sort(function (a, b) {
      return Number(b.name.toLowerCase().includes(query)) - Number(a.name.toLowerCase().includes(query))
        || a.name.localeCompare(b.name, 'en');
    });
    results.replaceChildren();
    found.forEach(function (node) {
      var button = element('button', node.name);
      button.type = 'button';
      button.append(element('small', node.kind + ' · ' + node.state));
      button.setAttribute('aria-pressed', String(node.id === selected));
      button.addEventListener('click', function () {
        select(node.id, true);
      });
      results.append(button);
    });
    results.hidden = !query;
    byId('brain-results-status').textContent = query
      ? found.length + (found.length === 1 ? ' matching record.' : ' matching records.') : '';
  }
  function showView(next) {
    view = next;
    document.querySelectorAll('[data-brain-view]').forEach(function (button) {
      button.setAttribute('aria-pressed', String(button.dataset.brainView === view));
    });
    byId('brain-map-view').hidden = view === 'journey';
    byId('brain-journey').hidden = view !== 'journey';
  }
  function setView(next) {
    if (next === 'controls' && nodes.get(selected).kind === 'capability') controlSelection = selected;
    showView(next);
    select(next === 'controls' ? controlSelection : moduleSelection, false);
  }
  document.querySelectorAll('[data-brain-view]').forEach(function (button) {
    button.addEventListener('click', function () { input.value = ''; setView(button.dataset.brainView); });
  });
  document.querySelectorAll('[data-brain-select]').forEach(function (button) {
    button.addEventListener('click', function () { select(button.dataset.brainSelect, true); });
  });
  input.addEventListener('input', filter);
  input.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') { input.value = ''; filter(); }
    if (event.key === 'Enter') { event.preventDefault(); var first = results.querySelector('button'); if (first) first.focus(); }
  });
  // A fragment into the catalogue remains usable even though enhancement initially collapses the long records.
  function revealHash() {
    var fragment;
    try { fragment = decodeURIComponent(location.hash.slice(1)); } catch (error) { return; }
    var target = byId(fragment);
    if (target) {
      for (var parent = target.parentElement; parent; parent = parent.parentElement) {
        if (parent.tagName === 'DETAILS') parent.open = true;
      }
    }
  }
  select(selected, false);
  records.open = false;
  document.querySelector('.brain-tools').hidden = false;
  document.querySelector('.brain-workspace').hidden = false;
  window.addEventListener('hashchange', revealHash);
  revealHash();
})();
