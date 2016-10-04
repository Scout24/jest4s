package de.is24.jest4s

import io.searchbox.client.{ JestClient, JestResult, JestResultHandler }
import scala.concurrent.Promise
import scala.concurrent.Future
import io.searchbox.action.Action

private class PromiseJestResultHandler[T <: JestResult] extends JestResultHandler[T]() {

  private val promise: Promise[T] = Promise()

  override def completed(result: T): Unit =
    if (result.isSucceeded)
      promise.success(result)
    else
      promise.failure(new ElasticSearchException(s"${result.getErrorMessage} - ${result.getJsonString}"))

  override def failed(exception: Exception): Unit = promise.failure(exception)

  def future: Future[T] = promise.future

}

object PromiseJestResultHandler {

  implicit class PromiseJestClient(jestClient: JestClient) {
    def executeAsyncPromise[T <: JestResult](clientRequest: Action[T]): Future[T] = {
      val promiseJestResultHandler: PromiseJestResultHandler[T] = new PromiseJestResultHandler[T]();
      jestClient.executeAsync(clientRequest, promiseJestResultHandler)
      promiseJestResultHandler.future
    }
  }

}

