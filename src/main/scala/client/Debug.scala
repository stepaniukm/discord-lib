package client

object Debug {
  val debugEnabled = true;

  def log(x: Any): Unit = {
    if(debugEnabled) {
      println(x);
    }
  }
}
