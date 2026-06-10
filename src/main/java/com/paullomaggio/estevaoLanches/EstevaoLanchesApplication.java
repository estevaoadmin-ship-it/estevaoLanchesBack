package com.paullomaggio.estevaoLanches;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
public class EstevaoLanchesApplication {

	public static void main(String[] args) {
		// MÁGICA NATIVA: Lê o arquivo .env e injeta no sistema antes do Spring subir
		try {
			var path = Paths.get(".env");
			if (Files.exists(path)) {
				Files.lines(path)
						.map(String::trim)
						.filter(line -> !line.isEmpty() && !line.startsWith("#"))
						.forEach(line -> {
							String[] parts = line.split("=", 2);
							if (parts.length == 2) {
								System.setProperty(parts[0].trim(), parts[1].trim());
							}
						});
			}
		} catch (IOException e) {
			System.err.println("Não foi possível carregar o arquivo .env: " + e.getMessage());
		}

		SpringApplication.run(EstevaoLanchesApplication.class, args);
	}
}