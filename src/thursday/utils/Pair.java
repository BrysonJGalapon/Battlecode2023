package thursday.utils;

public class Pair<F, S> {
  public final F first;
  public final S second;

  private Pair(F first, S second) {
    this.first = first;
    this.second = second;
  }

  public static <F, S> Pair<F, S> of(F first, S second) {
    return new Pair(first, second);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof Pair)) {
      return false;
    }

    Pair other = (Pair) o;
    return this.first.equals(other.first) && this.second.equals(other.second);
  }

  @Override
  public int hashCode() {
    int result = 17;

    result = 31 * result + this.first.hashCode();
    result = 31 * result + this.second.hashCode();
    return result;
  }
}
