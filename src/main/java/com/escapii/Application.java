package com.escapii;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class Application {

	public static void main(String[] args) {
		// Hosting platforma (Render) radi u UTC - bez ovoga LocalDateTime.now()
		// (createdAt, revealSentAt, invoiceSentAt, itd. svuda u kodu) vraća UTC vreme
		// koje se u admin panelu prikazivalo kao da je već beogradsko (2h razlike leti).
		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Belgrade"));
		SpringApplication.run(Application.class, args);
	}

}
