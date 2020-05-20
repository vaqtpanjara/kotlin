@java.lang.annotation.Retention(value = java.lang.annotation.RetentionPolicy.RUNTIME)
public abstract @interface Anno /* Anno*/ {
  @null()
  public abstract int[] ia();//  ia()

  @null()
  public abstract int[] ia2() default {1, 2, 3};//  ia2()

  @null()
  public abstract java.lang.String value() default "a";//  value()

  public abstract double d() default 0.0;//  d()

  public abstract int i();//  i()

  public abstract int j() default 5;//  j()

}