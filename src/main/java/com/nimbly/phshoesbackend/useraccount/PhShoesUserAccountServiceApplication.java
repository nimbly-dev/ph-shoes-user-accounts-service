package com.nimbly.phshoesbackend.useraccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.nimbly.phshoesbackend.useraccount")
public class PhShoesUserAccountServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(PhShoesUserAccountServiceApplication.class, args);
	}
}