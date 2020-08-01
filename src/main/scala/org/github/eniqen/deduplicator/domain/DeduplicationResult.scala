package org.github.eniqen.deduplicator.domain

/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */

trait DeduplicationResult[T] extends Product with Serializable {
  def get: T
}

object DeduplicationResult {
  case class Exist[T](get: T) extends DeduplicationResult[T]
  case class NotExist[T](get: T) extends DeduplicationResult[T]

  def exist[T](t: T): DeduplicationResult[T] = Exist(t)
  def notExist[T](t: T): DeduplicationResult[T] = NotExist(t)
}
