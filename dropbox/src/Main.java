import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
        Scanner kb = new Scanner(System.in);

        System.out.println("Enter you next guss");
        char player = kb.next().charAt(0);
        System.out.println(player);

    }
}