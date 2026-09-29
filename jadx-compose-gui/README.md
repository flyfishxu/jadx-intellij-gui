# Jadx Compose

The Compose Desktop + Jewel GUI module in the jadx repository, alongside `jadx-gui` and `jadx-cli`. Its window chrome, project tree, tabs, split panes, settings controls, banners and icons use JetBrains Jewel. The code reader is Compose, with JetBrains Mono, syntax colors, line numbers, selection/copy, horizontal and vertical scrolling, find-in-file and symbol navigation.

## Run

Requires **JetBrains Runtime (JBR) 25 SDK**. Jewel's decorated windows require JBR; a generic OpenJDK is insufficient. Gradle uses an installed JBR 25 toolchain. `JBR_HOME` can override the runtime used for launching and packaging.

From the repository root:

```sh
./gradlew :jadx-compose-gui:run
./gradlew :jadx-compose-gui:run --args="/absolute/path/application.apk --mapping /absolute/path/mapping.txt"
./gradlew :jadx-compose-gui:test
./gradlew :jadx-compose-gui:createDistributable
```

Open the repository root as the Gradle project in IntelliJ IDEA. `jadx-compose-gui`, `jadx-gui`, `jadx-cli` and the core/plugins are subprojects of the same build. Use the root Gradle wrapper; this module has no separate settings or wrapper. The existing CLI/Swing GUI launch and distribution tasks remain available.

Supported inputs: APK, DEX, JAR, CLASS and SMALI. Loading and decompilation run on a dedicated worker. Switching inputs safely disposes the prior session. Failed opens retain the current project. Resources support decoded XML and bounded text preview; binary formats do not have a viewer yet.

The project tree expands each package segment and resource directory separately. Use the project filter to find class/resource names, single-click to select a row, and double-click a class to open/decompile it. Selection fills the tree row, including its empty trailing area. On macOS, File, Search, View and Window are native menus in the system menu bar beside the Apple/application menu. Windows/Linux use Jewel menus in the window toolbar. macOS has no duplicate menu or action row inside the window. The Java / Smali menu in the bottom status bar switches the active class between decompiled source and original bytecode; each mode retains its own scroll and selection. The full-height structure sidebar sits alongside the editor and jumps to field and method declarations. Its full-row selection follows the current member, remains visible after focus moves to the editor, and scrolls into view. The editor highlights the current line and reveals both axes when navigating. Settings open as a single closable editor tab alongside code tabs; switching back preserves the code selection and scroll position. The title bar shows the current input filename with a recent-file dropdown (names and paths), a subtle gradient and one theme-toggle icon. Thin themed dividers separate both tool stripes from their panels. Settings persist interface language (system/English/Chinese), theme (system/light/light header/dark), and editor font size. Recent files are stored locally with Java Preferences, separately from the Swing GUI.

| Shortcut | Action |
| --- | --- |
| Cmd/Ctrl+click / Cmd/Ctrl+B | Go to declaration in Java source |
| Enter in project tree | Open selected file |
| Cmd/Ctrl+O | Open file |
| Cmd/Ctrl+W | Close active tab/settings |
| Cmd/Ctrl+F | Toggle find in file |
| Cmd/Ctrl+Shift+F | Project search |
| Enter / Shift+Enter in find | Next / previous match |
| Cmd/Ctrl+L | Focus project filter |
| Cmd/Ctrl+, | Settings |

Holding Cmd/Ctrl while hovering a resolvable Java symbol shows a blue underlined link with a hand cursor. Pressing or releasing the modifier updates the link even with a stationary mouse or focus in another workspace control. Hover resolution is delayed briefly, cached per document and performed on the session worker; it never opens a tab. Java declaration navigation uses jadx code metadata, including mapped names, overloaded methods, fields, inner classes and local variables. Targets absent from the input (such as external libraries or optimized-away declarations) cannot be opened. Smali declaration navigation is not implemented. On macOS/JBR, Force Click stage 2 is connected to the same action via the optional Apple pressure-event bridge; physical trackpad behavior still needs hardware verification.

## Build and package boundaries

This directory is **a Gradle subproject registered in the root `settings.gradle.kts`**, alongside `jadx-gui`. Dependencies use `project(":jadx-core")` and `project(":jadx-plugins:...")`, so core, input plugins and rename mappings are built directly from the same checkout. No composite build, dependency substitution or published jadx binaries are used.

Plugin and library versions live in the root version catalog. The module uses the repository's jadx version, with a numeric fallback for development native installers. Only this GUI configures JBR 25; the core and existing GUI retain their Java 11 bytecode targets. Runtime calls go through the Java API (`JadxDecompiler`, `JavaClass`, resources and mapping plugin); the Compose frontend does not embed the Swing GUI.

The Kotlin source tree separates responsibilities:

```text
jadx/compose/
  Main.kt               application entry point and window composition
  decompiler/           jadx session, source documents, mapping and project search
  preferences/          persisted appearance, language, font size and recent files
  ui/
    components/         shared Jewel controls and tool-window buttons
    editor/             code reader, syntax highlighting, structure and mode menu
    model/              workspace state, background jobs and tab selection
    project/            package/resource hierarchy and project tree
    search/             search controls and results
    settings/           settings tab content
    window/             native macOS menus, fallback menus and theme toggle
    workspace/          editor tabs, split layout, status bar and welcome view
```

UI packages depend on immutable source documents and workspace state; jadx API calls stay in `decompiler`. Tests mirror the relevant packages, with private artifact configuration isolated in `fixtures`.

## Mapping and search

Choose **File → Import mapping…** for an R8 / ProGuard mapping. Importing or removing a mapping reloads the session, preserves open tabs by their original class IDs, and retains the old project if the operation fails. Input APKs and mapping files remain read-only.

R8 records are streamed into a temporary position-free mapping before the official jadx mapping plugin imports them in reverse. This handles ordinary method records with original-line suffixes, which the current upstream ProGuard reader skips. Shared obfuscated line ranges retain the outermost inline frame. Temporary files are deleted when the session closes. This provides declaration renaming, not Retrace: optimized-away declarations, private obfuscation, residual-signature changes and all original source/debug information cannot be reconstructed from names alone. Smali intentionally preserves the actual DEX identifiers and instructions.

Project search supports classes, methods, fields, Java text, Smali text, and resource names/decoded text. Searches accept case sensitivity, regex and a package/path filter; results navigate to source positions. A maximum of 500 results keeps the panel bounded. Text searches decompile lazily on the session worker and can be cancelled between classes; a single long-running jadx decompilation must finish before cancellation takes effect. Skipped unreadable resources and invalid expressions are surfaced in the panel.

## Upstream updates

Frontend code lives under **`jadx-compose-gui/`**. Root integration is limited to the module registration, the IntelliJ artifact repository and the Compose/Jewel entries in the shared version catalog. Upstream core sources and build conventions are unchanged. `DecompilerSession.kt`, `Search.kt` and `R8MappingFile.kt` isolate the jadx integration from the UI.

Keep these small root build changes when merging upstream. Changes to the same settings/catalog entries may require a merge resolution, and jadx API or build changes may require compatibility work; this arrangement cannot guarantee conflict-free updates.

To update after committing/stashing local work:

```sh
# Once, if not configured:
git remote add upstream https://github.com/skylot/jadx.git

git fetch upstream
git merge upstream/master
./gradlew :jadx-compose-gui:test :jadx-compose-gui:createDistributable
```

The tests decompile upstream's real DEX fixture and a freshly compiled JAR, verify mapping direction and inline-frame handling, search/source positions, nested package/resource trees, text/binary resources, and code-reader interactions. Run UI checks in a desktop session; screenshots are written under `build/reports/ui/`.

Optional private release checks accept Onion Store's APK and mapping without copying either into the repository:

```sh
./gradlew :jadx-compose-gui:test \
  -PtestApk=/absolute/path/app-release.apk \
  -PtestMapping=/absolute/path/mapping.txt
```

These checks import/remove mappings through the menu, validate restored `AdbInstaller.ensureIdentity` / `identityReady`, exercise all six search scopes, open search results, switch Java/Smali, and hash both artifacts before/after. For the WearOS Toolbox fixture use `-PtestFixture=wear-toolbox`; that check targets `AdbCoreInitializer` instead. Normal tests skip these private-fixture checks when paths are omitted.

## Scope

This is a usable Compose frontend, not feature parity with the mature Swing GUI. Debugging, usages/cross-reference navigation, editing/exporting rename mappings, plugin management and image/hex viewers are not implemented. The adapter currently uses jadx's `ResContainer` to decode resource previews; upstream changes there need an adapter update. Very large single classes still use a whole-document Compose text layout.
