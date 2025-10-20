package com.nimbly.phshoesbackend.useraccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(scanBasePackages = {
		"com.nimbly.phshoesbackend.useraccount",
		"com.nimbly.phshoesbackend.notification.core",
		"com.nimbly.phshoesbackend.notification.email.providers",
		"com.nimbly.phshoesbackend.services.common.core"
})
public class PhShoesUserAccountServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(PhShoesUserAccountServiceApplication.class, args);
	}
}