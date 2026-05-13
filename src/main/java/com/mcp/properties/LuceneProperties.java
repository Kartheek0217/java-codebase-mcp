package com.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "lucene.ram")
public class LuceneProperties {

	/**
	 * The size of the RAM buffer in MB used by Lucene IndexWriter. Default value is
	 * 64.0 MB.
	 */
	private double bufferSize;

	public double getBufferSize() {
		if (bufferSize == 0.0 || bufferSize < 64.0) {
			return 64.0;
		}
		return bufferSize;
	}

	public void setBufferSize(double bufferSize) {
		this.bufferSize = bufferSize;
	}
}
