// Garante que o app consiga ler o cabeçalho de Authorization na resposta
        config.setExposedHeaders(Collections.singletonList("Authorization"));