package com.nimbly.phshoesbackend.useraccount.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
		"com.nimbly.phshoesbackend.useraccount.core",
		"com.nimbly.phshoesbackend.useraccount.web",
		"com.nimbly.phshoesbackend.notification.core",
		"com.nimbly.phshoesbackend.notification.email.providers",
		"com.nimbly.phshoesbackend.services.common.core"
})
public class PhShoesUserAccountServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(PhShoesUserAccountServiceApplication.class, args);
	}
}
