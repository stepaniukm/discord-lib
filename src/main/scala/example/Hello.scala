package example

import sys.env
import client.Client

object Example extends App {
  val tokenMaybe = env.get("TOKEN");

  tokenMaybe.map { (token) => {
      val client = new Client(token);
    }
  }
}
