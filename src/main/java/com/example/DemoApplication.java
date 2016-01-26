package com.example;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.annotation.Aggregator;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Splitter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@MessageEndpoint
@IntegrationComponentScan
public class DemoApplication {

	@Autowired
	Sender sender;

	@Bean
	@ConditionalOnMissingBean
	public Sampler defaultTraceSampler() {
		return new AlwaysSampler();
	}

	@RequestMapping("/greeting")
	public Greeting greeting() {
		String message = "Hello World!";
		this.sender.send(message);
		return new Greeting(message);
	}

	@Splitter(inputChannel="greetings", outputChannel="words")
	public List<String> words(String greeting) {
		return Arrays.asList(StringUtils.delimitedListToStringArray(greeting, " "));
	}

	@Aggregator(inputChannel="words", outputChannel="counts")
	public int count(List<String> greeting) {
		return greeting.size();
	}

	@ServiceActivator(inputChannel="counts")
	public void report(int count) {
		System.err.println("Count: " + count);
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(DemoApplication.class, args);
	}

	static Path resourceToPath(URL resource) {

		Objects.requireNonNull(resource, "Resource URL cannot be null");
		URI uri;
		try {
			uri = resource.toURI();
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("Could not extract URI", e);
		}

		String scheme = uri.getScheme();
		if (scheme.equals("file")) {
			String path = uri.toString().substring("file:".length());
			if (path.contains("//")) {
				path = StringUtils.cleanPath(path.replace("//", ""));
			}
			return Paths.get(new FileSystemResource(path).getFile().toURI());
		}

		if (!scheme.equals("jar")) {
			throw new IllegalArgumentException("Cannot convert to Path: " + uri);
		}

		String s = uri.toString();
		int separator = s.indexOf("!/");
		String entryName = s.substring(separator + 1);
		URI fileURI = URI.create(s.substring(0, separator));

		System.err.println(s);
		FileSystem fs;
		try {
			fs = FileSystems.newFileSystem(fileURI,
					Collections.<String, Object>emptyMap());
			return fs.getPath(entryName);
		}
		catch (IOException e) {
			throw new IllegalArgumentException(
					"Could not create file system for resource: " + resource, e);
		}
	}

}

@MessagingGateway(name = "greeter")
interface Sender {
	@Gateway(requestChannel = "greetings")
	void send(String message);
}

class Greeting {
	private String message;

	Greeting() {
	}

	public Greeting(String message) {
		super();
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}
