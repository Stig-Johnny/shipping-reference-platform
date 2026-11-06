# Third-party notices

This repository's own code, configuration, models, specifications, ADRs, security reviews and documentation are
licensed under the Apache License 2.0 (`LICENSE`). Two kinds of third-party material are committed to it, and each
keeps its own licence. Dependencies that the build only resolves (Maven artifacts, the JDK, GitHub Actions) are not
redistributed here. Their licences are recorded in their reviews in `security/reviews/`.

## Mermaid 11.17.2 (`tools/docs/vendor/mermaid/11.17.2/mermaid.min.js`)

The unmodified `dist/mermaid.min.js` from the npm package `mermaid@11.17.2`
(<https://github.com/mermaid-js/mermaid>). Its review is `security/reviews/vendored-js--mermaid--11.17.2.md`.

```text
The MIT License (MIT)

Copyright (c) 2014 - 2022 Knut Sveidqvist

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The bundle embeds other packages. The minified file keeps the licence comments its build preserved (for lodash-es,
DOMPurify and parts of cytoscape, among others). The review inventoried the embedded components, with their licences:

| Components | Licence |
|---|---|
| @mermaid-js/parser, langium, chevrotain-allstar | MIT |
| chevrotain and @chevrotain/* | Apache-2.0 |
| vscode-languageserver, -protocol, -types, -textdocument, vscode-jsonrpc, vscode-uri | MIT |
| DOMPurify 3.4.12 | Apache-2.0 OR MPL-2.0 |
| lodash-es | MIT |
| KaTeX 0.16.47 | MIT |
| d3 7.9.0 and its d3-* sub-packages | ISC |
| d3-sankey, with d3-array 2, d3-shape 1 and d3-path 1 | BSD-3-Clause |
| dagre-d3-es, cytoscape, cytoscape-cose-bilkent, cytoscape-fcose, cose-base, layout-base | MIT |
| marked, dayjs, stylis, @braintree/sanitize-url, es-toolkit, uuid, ts-dedent, fastdom, @upsetjs/venn.js, roughjs and its helpers, @iconify/utils | MIT |
| khroma 2.1.0 | MIT, per its repository (<https://github.com/fabiospampinato/khroma>); its npm package declares no licence |

## Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`)

The wrapper of Gradle 9.8.2, "Copyright © 2015 the original authors", licensed under the Apache License 2.0
(<https://www.apache.org/licenses/LICENSE-2.0>). Its review is `security/reviews/gradle-distribution--gradle--9.8.2.md`.
