
public class Reproduce {
    public static void main(String[] args) {
        TextManipulator tm = new TextManipulator("Hello World");
        System.out.println("Initial: cursor=" + tm.getCursor());
        tm.processCommands("rh");
        System.out.println("After 'rh': cursor=" + tm.getCursor());
    }
}
