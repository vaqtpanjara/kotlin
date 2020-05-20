public final class AnnotatedParameterInInnerClassConstructor /* test.AnnotatedParameterInInnerClassConstructor*/ {
  @null()
  public  AnnotatedParameterInInnerClassConstructor();//  .ctor()



public final class Inner /* test.AnnotatedParameterInInnerClassConstructor.Inner*/ {
  @null()
  public  Inner(@org.jetbrains.annotations.NotNull() @test.Anno(x = "a") java.lang.String, @org.jetbrains.annotations.NotNull() @test.Anno(x = "b") java.lang.String);//  .ctor(java.lang.String, java.lang.String)

}public final class InnerGeneric /* test.AnnotatedParameterInInnerClassConstructor.InnerGeneric*/<T>  {
  @null()
  public  InnerGeneric(@null() @test.Anno(x = "a") T, @org.jetbrains.annotations.NotNull() @test.Anno(x = "b") java.lang.String);//  .ctor(T, java.lang.String)

}}