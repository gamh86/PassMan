package passworddata;

//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordEntry
{
	@JsonProperty
	private String email;
	@JsonProperty
	private String password;
	@JsonProperty
	private String username;
	@JsonProperty
	private long timestamp;
	@JsonProperty
	private int charsetSize; // so we can calculate stats in doAnalysePassword()
	@JsonProperty
	private String hash;

	public PasswordEntry() { }

	public String getEmail() { return email; }
	public String getPassword() { return password; }
	public String getUsername() { return username; }
	public long getTimestamp() { return timestamp; }
	public int getCharsetSize() { return charsetSize; }
	public String getHash() { return hash; }

	public void setEmail(String newEmail) { email = newEmail; }
	public void setPassword(String newPass) { password = newPass; }
	public void setUsername(String newUsername) { username = newUsername; }
	public void setTimestamp(long newTimestamp) { timestamp = newTimestamp; }
	public void setCharsetSize(int size) { charsetSize = size; }
	public void setHash(String h) { hash = h; }
}
