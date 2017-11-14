package ua.kiev.prog.simple;

public class CustomClient {
    private final String name;
    private final int age;

    public CustomClient(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }
}
