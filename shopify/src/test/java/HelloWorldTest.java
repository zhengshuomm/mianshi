import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class HelloWorldTest {
    @Test
    void testGetGreeting() {
        assertEquals("Hello, World11!", HelloWorld.getGreeting());
    }
}
