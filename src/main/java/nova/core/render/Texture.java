package nova.core.render;

/**
 * A texture has a file location.
 * @author Calclavia
 */
public class Texture {

	public final String domain;
	public final String resource;

	public Texture(String domain, String resource) {
		this.domain = domain;
		this.resource = resource;
	}

	public String getResource() {
		return domain + ":" + resource;
	}
}
