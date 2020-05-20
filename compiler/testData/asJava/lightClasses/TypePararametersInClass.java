public abstract class A /* A*/<T extends A<T>>  extends B<java.util.Collection<? extends T>> implements C<T> {
  @null()
  public  A();//  .ctor()



public class Inner /* A.Inner*/<D>  extends B<java.util.Collection<? extends T>> implements C<D> {
  @null()
  public  Inner();//  .ctor()

}public final class Inner2 /* A.Inner2*/<X>  extends A<T>.Inner<X> implements C<X> {
  @null()
  public  Inner2();//  .ctor()

}}