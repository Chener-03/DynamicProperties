package xyz.chener.ext.dp;

public class TestJavaEntity {

    @PropertiesKey("name")
    private String name;

    @PropertiesKey(value = "age",clazz = Integer.class)
    private Integer age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "TestJavaEntity{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}
