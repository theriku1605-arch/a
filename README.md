# RectMiner 0.1.0 — Minecraft Java 1.7.10 / Forge 10.13.4.1614

**Estado:** prototipo de código fuente; todavía no está compilado ni probado dentro del juego. No es un JAR listo para instalar.

## Controles
- **Numpad 1:** marca el punto A mirando un bloque.
- **Numpad 2:** marca el punto B mirando un bloque.
- **Numpad 3:** inicia o pausa el intento de minería automática.
- **Numpad 0:** parada de emergencia.

El área se limita a 12.000 bloques por seguridad.

## Limitaciones importantes
Es un prototipo experimental, no un sistema de navegación completo como Baritone. Intenta avanzar hacia bloques de la zona y romper bloques que pueda alcanzar mirando hacia ellos. Puede atascarse ante paredes, desniveles, agua, lava, caídas, entidades o bloques inaccesibles. No garantiza que pueda recorrer y minar toda la caja de forma fiable.

Es un mod pensado para ejecutarse en el cliente. No requiere añadir este mod al servidor, pero el servidor puede rechazar acciones o sancionar automatización si sus reglas lo prohíben. Úsalo solo donde esté permitido.

## Compilar en GitHub Actions
1. Descarga y descomprime este ZIP.
2. Abre la carpeta `RectMiner1710-fixed`.
3. En GitHub, sube **todo el contenido de esa carpeta** al nivel principal del repositorio. Deben aparecer `build.gradle`, `settings.gradle`, `src` y `.github` directamente en la página principal del repositorio. No subas solo el ZIP ni la carpeta exterior.
4. Abre **Actions** y ejecuta **Build RectMiner (Minecraft 1.7.10)**, o haz un commit para disparar la compilación automáticamente.
5. Si termina en verde, abre la ejecución y descarga el artefacto `RectMiner-1.7.10` en **Artifacts**. El JAR está dentro del ZIP descargado.
6. Si falla, abre el paso **Build mod** y comparte el error completo. ForgeGradle 1.2 es muy antiguo y pueden surgir incompatibilidades externas; el flujo automatizado no garantiza una compilación correcta hasta que termine con éxito.

El flujo espera que `build.gradle`, `settings.gradle`, `src` y `.github` estén en la raíz del repositorio. Se cambió a ForgeGradle 1.2.11 desde JitPack para evitar la URL antigua de Minecraft que provocaba `FileNotFoundException` en `:downloadClient`, y usa Gradle 4.4.1 con Java 8. No puedo afirmar que compila hasta que GitHub Actions termine en verde.
