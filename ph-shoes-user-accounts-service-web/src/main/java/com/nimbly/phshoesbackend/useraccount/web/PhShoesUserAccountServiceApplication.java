package com.nimbly.phshoesbackend.useraccount.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.nimbly.phshoesbackend.commons.core.autoconfig.EmailCryptoAutoConfiguration;

@SpringBootApplication
@ImportAutoConfiguration(EmailCryptoAutoConfiguration.class)
@ComponentScan(basePackages = {
		"com.nimbly.phshoesbackend.useraccount.core",
		"com.nimbly.phshoesbackend.useraccount.web",
		"com.nimbly.phshoesbackend.notification.core",
		"com.nimbly.phshoesbackend.notification.email.providers",
		"com.nimbly.phshoesbackend.commons.core",
		"com.nimbly.phshoesbackend.commons.web"
})
public class PhShoesUserAccountServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(PhShoesUserAccountServiceApplication.class, args);
	}
}
