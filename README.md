# GUARDIAN Android

Aplicativo Android nativo em Kotlin + Jetpack Compose, desenvolvido para funcionar sem root.

## Recursos iniciais
- Lista de aplicativos com atividade de inicialização visível ao Android.
- Explorador de APK selecionado pelo usuário: lista entradas internas do pacote ZIP.
- Editor de arquivos de texto/configuração selecionados pelo usuário.
- Salvamento como cópia para preservar o original.
- Atalho para as configurações de permissão de sobreposição.

## Limites do Android
O sandbox impede que um aplicativo comum leia livremente a memória ou diretórios privados de outros apps. GUARDIAN não injeta código, não altera processos de terceiros e não contorna proteções. Testes internos devem usar arquivos exportados ou uma API de depuração implementada pelo próprio jogo.

## Compilação
Requer JDK 17, Android SDK 35 e Gradle 8.9.

```bash
gradle assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`.

## Estado
Base inicial enviada ao repositório. O painel flutuante real, integração de depuração autorizada, testes e APK compilado ainda estão pendentes.
