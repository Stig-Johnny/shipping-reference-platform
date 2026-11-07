package io.github.stigjohnny.c4contractkit.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * A synthetic repository for the web import check: a model with a shop's web container, a web root whose files
 * conform to it, the app's package.json and an allowlist that approves its packages and the Node.js its tests run on.
 * Each negative fixture changes one file, one line of the model, the package.json or the allowlist.
 *
 * <p>Components: {@code shell} (app), {@code cart} (features/cart, entry index.ts), {@code catalog}
 * (features/catalog, entry index.ts), {@code widgets} (ui), {@code client} (api, the only one that may import
 * {@code @shop/client}) and the planned {@code admin} (features/admin). Tests may import
 * {@code @testing-library/react}, {@code node:test} and {@code node:assert/strict}.
 */
public final class WebFixture {

    public static final String MODEL = """
            workspace "Shop" {
                !identifiers flat
                !impliedRelationships false
                model {
                    shop = softwareSystem "Shop" {
                        web = container "Web" {
                            properties {
                                "web.root" "web/src"
                                "web.testPackages" "@testing-library/react"
                                "web.testBuiltins" "node:test, node:assert/strict"
                            }
                            shell = component "Shell" {
                                properties {
                                    "web.id" "shell"
                                    "web.paths" "app"
                                    "web.status" "built"
                                    "web.packages" "react, react-dom"
                                }
                            }
                            cart = component "Cart" {
                                properties {
                                    "web.id" "cart"
                                    "web.paths" "features/cart"
                                    "web.status" "built"
                                    "web.entry" "features/cart/index.ts"
                                    "web.packages" "react"
                                }
                            }
                            catalog = component "Catalog" {
                                properties {
                                    "web.id" "catalog"
                                    "web.paths" "features/catalog"
                                    "web.status" "built"
                                    "web.entry" "features/catalog/index.ts"
                                }
                            }
                            widgets = component "Widgets" {
                                properties {
                                    "web.id" "widgets"
                                    "web.paths" "ui"
                                    "web.status" "built"
                                    "web.packages" "react"
                                }
                            }
                            client = component "Client" {
                                properties {
                                    "web.id" "client"
                                    "web.paths" "api"
                                    "web.status" "built"
                                    "web.packages" "@shop/client"
                                }
                            }
                            admin = component "Admin" "" "" "Planned" {
                                properties {
                                    "web.id" "admin"
                                    "web.paths" "features/admin"
                                    "web.status" "planned"
                                }
                            }
                        }
                    }
                    shell -> cart "Routes to" "" "Required import" {
                        properties {
                            "web.mode" "REQUIRED"
                        }
                    }
                    shell -> catalog "Routes to" "" "Required import" {
                        properties {
                            "web.mode" "REQUIRED"
                        }
                    }
                    shell -> widgets "Uses" {
                        properties {
                            "web.mode" "PERMITTED"
                        }
                    }
                    shell -> admin "Will route to" "" "Required import" {
                        properties {
                            "web.mode" "REQUIRED"
                        }
                    }
                    cart -> client "Loads the cart through" "" "Required import" {
                        properties {
                            "web.mode" "REQUIRED"
                        }
                    }
                    cart -> widgets "Renders with" "" "Required import" {
                        properties {
                            "web.mode" "REQUIRED"
                        }
                    }
                    catalog -> client "Loads items through" "" "Required import" {
                        properties {
                            "web.mode" "REQUIRED"
                        }
                    }
                    catalog -> widgets "May render with" {
                        properties {
                            "web.mode" "PERMITTED"
                        }
                    }
                    catalog -> cart "Adds items to" {
                        properties {
                            "web.mode" "PERMITTED"
                        }
                    }
                }
            }
            """;

    /** The conformant web root, by path relative to it. */
    public static Map<String, String> files() {
        Map<String, String> files = new TreeMap<>();
        files.put("app/main.tsx", """
                import { createRoot } from 'react-dom/client';
                import { Shell } from './Shell';
                import { Button } from '../ui/Button';

                createRoot(document.getElementById('root')!).render(<Shell><Button>Start</Button></Shell>);
                """);
        files.put("app/Shell.tsx", """
                import { CartPage } from '../features/cart';
                import { CatalogPage } from '../features/catalog/';

                export function Shell() {
                    return <main><CatalogPage /><CartPage /></main>;
                }
                """);
        files.put("features/cart/index.ts", """
                export { CartPage } from './CartPage';
                export type { CartLine } from './CartLine';
                """);
        files.put("features/cart/CartLine.ts", "export type CartLine = { sku: string; quantity: number };\n");
        files.put("features/cart/CartPage.tsx", """
                import { useState } from 'react';
                import { Button } from '../../ui/Button';
                import { loadCart } from '../../api/cart';

                export function CartPage() {
                    const [lines] = useState(loadCart());
                    return <section><p>Don't leave: {lines.length} items.</p><Button>Check out</Button></section>;
                }
                """);
        files.put("features/cart/CartPage.test.tsx", """
                import assert from 'node:assert/strict';
                import { test } from 'node:test';
                import { render } from '@testing-library/react';
                import { CartPage } from './CartPage';

                test('renders', () => assert.ok(render(<CartPage />)));
                """);
        files.put("features/catalog/index.ts", "export * from './CatalogPage';\n");
        files.put("features/catalog/CatalogPage.tsx", """
                import type { CartLine } from '../cart';
                import { loadItems } from '../../api/items';

                export function CatalogPage() {
                    const first: CartLine | undefined = undefined;
                    return <ul>{loadItems().map((item) => <li key={item}>{item}</li>)}{String(first)}</ul>;
                }
                """);
        files.put("ui/Button.tsx", """
                import type { ReactNode } from 'react';

                export function Button({ children }: { children: ReactNode }) {
                    return <button type="button">{children}</button>;
                }
                """);
        files.put("ui/theme.css", ":root { --action: #0f766e; }\n");
        files.put("api/cart.ts", """
                import { client } from '@shop/client';

                export const loadCart = (): string[] => client.get('/cart');
                """);
        files.put("api/items.ts", """
                import { client } from '@shop/client/items';

                export const loadItems = (): string[] => client.get('/items');
                """);
        return files;
    }

    /** The app's package.json: exact versions of the shop's packages, and the Node.js its tests run on. */
    public static final String PACKAGE_JSON = """
            {
              "name": "shop-web",
              "private": true,
              "engines": { "node": "26.13.0" },
              "dependencies": { "react": "19.3.0", "react-dom": "19.3.0", "@shop/client": "1.0.0" },
              "devDependencies": { "@testing-library/react": "16.3.3" }
            }
            """;

    /** The allowlist: each package approved for the app (web) in its scope, and Node.js 26.13.0 for the web job. */
    public static final String ALLOWLIST = """
            {
              "schemaVersion": 1,
              "entries": [
                %s,
                %s,
                %s,
                %s,
                { "kind": "runtime", "coordinate": "nodejs.org/dist node", "version": "26.13.0", "project": "ci",
                  "configuration": ["web", " contract-e2e jobs"], "verdict": "APPROVE-WITH-CONDITIONS",
                  "reviewRecord": "security/reviews/runtime--node--26.13.0.md" }
              ]
            }
            """.formatted(npm("react", "19.3.0", "dependencies"), npm("react-dom", "19.3.0", "dependencies"),
            npm("@shop/client", "1.0.0", "dependencies"), npm("@testing-library/react", "16.3.3", "devDependencies"));

    private WebFixture() {
    }

    /** An approving npm entry for the app web. */
    public static String npm(String name, String version, String configuration) {
        return npm(name, version, configuration, "APPROVE");
    }

    /** An npm entry for the app web with this verdict. */
    public static String npm(String name, String version, String configuration, String verdict) {
        return ("{ \"kind\": \"npm\", \"coordinate\": \"%s\", \"version\": \"%s\", \"project\": \"web\", "
                + "\"configuration\": [\"%s\"], \"verdict\": \"%s\", \"reviewRecord\": "
                + "\"security/reviews/npm--%s.md\" }").formatted(name, version, configuration, verdict,
                name.replace("@", "").replace('/', '-'));
    }

    /** Writes a repository with the model, the files, the package.json and the allowlist of the fixture. */
    public static Path write(Path repository, String model, Map<String, String> files) {
        return write(repository, model, files, PACKAGE_JSON, ALLOWLIST);
    }

    /**
     * Writes a repository with the model at architecture/web.dsl, the files under web/src, the package.json at
     * web/package.json and the allowlist at security/allowlist.json; a null package.json or allowlist is not written.
     */
    public static Path write(Path repository, String model, Map<String, String> files,
                             @org.jspecify.annotations.Nullable String packageJson,
                             @org.jspecify.annotations.Nullable String allowlist) {
        try {
            Files.createDirectories(repository.resolve("architecture"));
            Files.writeString(repository.resolve("architecture/web.dsl"), model, StandardCharsets.UTF_8);
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path path = repository.resolve(file.getKey().startsWith("/") ? file.getKey().substring(1)
                        : "web/src/" + file.getKey());
                Files.createDirectories(path.getParent());
                Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            }
            if (packageJson != null) {
                Files.createDirectories(repository.resolve("web"));
                Files.writeString(repository.resolve("web/package.json"), packageJson, StandardCharsets.UTF_8);
            }
            if (allowlist != null) {
                Files.createDirectories(repository.resolve("security"));
                Files.writeString(repository.resolve("security/allowlist.json"), allowlist, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return repository.resolve("architecture/web.dsl");
    }
}
