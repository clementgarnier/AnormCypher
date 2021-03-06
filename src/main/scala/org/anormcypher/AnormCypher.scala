package org.anormcypher

import MayErr._
import collection.TraversableOnce
import scala.collection.JavaConverters._

abstract class CypherRequestError
case class ColumnNotFound(columnName: String, possibilities: List[String]) extends CypherRequestError {
  override def toString = columnName + " not found, available columns : " + possibilities.map { p => p.dropWhile(c => c == '.') }
    .mkString(", ")
}

case class TypeDoesNotMatch(message: String) extends CypherRequestError
case class InnerTypeDoesNotMatch(message: String) extends CypherRequestError
case class UnexpectedNullableFound(on: String) extends CypherRequestError
case object NoColumnsInReturnedResult extends CypherRequestError
case class CypherMappingError(msg: String) extends CypherRequestError

trait Column[A] extends ((Any, MetaDataItem) => MayErr[CypherRequestError, A])

object Column {

  def apply[A](transformer: ((Any, MetaDataItem) => MayErr[CypherRequestError, A])): Column[A] = new Column[A] {

    def apply(value: Any, meta: MetaDataItem): MayErr[CypherRequestError, A] = transformer(value, meta)

  }

  def nonNull[A](transformer: ((Any, MetaDataItem) => MayErr[CypherRequestError, A])): Column[A] = Column[A] {
    case (value, meta @ MetaDataItem(qualified, _, _)) =>
      if (value != null) transformer(value, meta) else Left(UnexpectedNullableFound(qualified.toString))
  }

  implicit def rowToString: Column[String] = {
    Column.nonNull[String] { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case string: String => Right(string)
        case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to String for column " + qualified))
      }
    }
  }

  implicit def rowToInt: Column[Int] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case int: Int => Right(int)
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Int for column " + qualified))
    }
  }

  implicit def rowToDouble: Column[Double] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case d: Double => Right(d)
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Double for column " + qualified))
    }
  }

  implicit def rowToShort: Column[Short] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case short: Short => Right(short)
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Short for column " + qualified))
    }
  }

  implicit def rowToByte: Column[Byte] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case byte: Byte => Right(byte)
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Byte for column " + qualified))
    }
  }

  implicit def rowToBoolean: Column[Boolean] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bool: Boolean => Right(bool)
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Boolean for column " + qualified))
    }
  }

  implicit def rowToLong: Column[Long] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case int: Int => Right(int: Long)
      case long: Long => Right(long)
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Long for column " + qualified))
    }
  }

  implicit def rowToNeoNode: Column[NeoNode] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case node: java.util.LinkedHashMap[String,Any] => Neo4jREST.asNode(node)
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to NeoNode for column " + qualified))
    }
  }

  implicit def rowToNeoRelationship: Column[NeoRelationship] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case rel: java.util.LinkedHashMap[String,Any] => Neo4jREST.asRelationship(rel)
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to NeoRelationship for column " + qualified))
    }
  }
  
  implicit def rowToOption[T](implicit transformer: Column[T]): Column[Option[T]] = Column { (value, meta) =>
    if (value != null) transformer(value, meta).map(Some(_)) else (Right(None): MayErr[CypherRequestError, Option[T]])
  }

  def toSeq[A](value:Any, qualified:String, converter: (Any) => A) = {
    value match {
      case list: java.util.ArrayList[Any] => 
        try {
          Right(list.asScala.map { 
            e => e match { case s:Any => converter(s) }
          }.toIndexedSeq)
        } catch {
          case e:Exception => Left(InnerTypeDoesNotMatch(e.getMessage))
        }
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Seq[] for column " + qualified))
    }
  }

  implicit def rowToSeqString: Column[Seq[String]] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    toSeq[String](value, qualified, (s:Any) => s.toString)
  }

  implicit def rowToSeqInt: Column[Seq[Int]] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    toSeq[Int](value, qualified, (s:Any) => s.toString.toInt)
  }

  implicit def rowToSeqLong: Column[Seq[Long]] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    toSeq[Long](value, qualified, (s:Any) => s.toString.toLong)
  }

  implicit def rowToSeqDouble: Column[Seq[Double]] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    toSeq[Double](value, qualified, (s:Any) => s.toString.toDouble)
  }

  implicit def rowToSeqNeoRelationship: Column[Seq[NeoRelationship]] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    toSeq[NeoRelationship](value, qualified, (r:Any) => Neo4jREST.asRelationship(r).get)
  }

  implicit def rowToSeqNeoNode: Column[Seq[NeoNode]] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    toSeq[NeoNode](value, qualified, (n:Any) => Neo4jREST.asNode(n).get)
  }

  implicit def rowToBigInteger: Column[java.math.BigInteger] = Column.nonNull { (value, meta) =>
    import java.math.BigInteger
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bi: BigInteger => Right(bi)
      case int: Int => Right(BigInteger.valueOf(int))
      case long: Long => Right(BigInteger.valueOf(long))
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to BigInteger for column " + qualified))
    }
  }

  implicit def rowToBigDecimal: Column[java.math.BigDecimal] = Column.nonNull { (value, meta) =>
    import java.math.BigDecimal
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case bi: java.math.BigDecimal => Right(bi)
      case double: Double => Right(new java.math.BigDecimal(double))
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to BigDecimal for column " + qualified))
    }
  }

}

case class TupleFlattener[F](f: F)

trait PriorityOne {
  implicit def flattenerTo2[T1, T2]: TupleFlattener[(T1 ~ T2) => (T1, T2)] = TupleFlattener[(T1 ~ T2) => (T1, T2)] { case (t1 ~ t2) => (t1, t2) }
}

trait PriorityTwo extends PriorityOne {
  implicit def flattenerTo3[T1, T2, T3]: TupleFlattener[(T1 ~ T2 ~ T3) => (T1, T2, T3)] = TupleFlattener[(T1 ~ T2 ~ T3) => (T1, T2, T3)] { case (t1 ~ t2 ~ t3) => (t1, t2, t3) }
}

trait PriorityThree extends PriorityTwo {
  implicit def flattenerTo4[T1, T2, T3, T4]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4) => (T1, T2, T3, T4)] = TupleFlattener[(T1 ~ T2 ~ T3 ~ T4) => (T1, T2, T3, T4)] { case (t1 ~ t2 ~ t3 ~ t4) => (t1, t2, t3, t4) }
}

trait PriorityFour extends PriorityThree {
  implicit def flattenerTo5[T1, T2, T3, T4, T5]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5) => (T1, T2, T3, T4, T5)] = TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5) => (T1, T2, T3, T4, T5)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5) => (t1, t2, t3, t4, t5) }
}

trait PriorityFive extends PriorityFour {
  implicit def flattenerTo6[T1, T2, T3, T4, T5, T6]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6) => (T1, T2, T3, T4, T5, T6)] = TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6) => (T1, T2, T3, T4, T5, T6)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6) => (t1, t2, t3, t4, t5, t6) }
}

trait PrioritySix extends PriorityFive {
  implicit def flattenerTo7[T1, T2, T3, T4, T5, T6, T7]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7) => (T1, T2, T3, T4, T5, T6, T7)] = TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7) => (T1, T2, T3, T4, T5, T6, T7)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7) => (t1, t2, t3, t4, t5, t6, t7) }
}

trait PrioritySeven extends PrioritySix {
  implicit def flattenerTo8[T1, T2, T3, T4, T5, T6, T7, T8]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8) => (T1, T2, T3, T4, T5, T6, T7, T8)] = TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8) => (T1, T2, T3, T4, T5, T6, T7, T8)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8) => (t1, t2, t3, t4, t5, t6, t7, t8) }
}

trait PriorityEight extends PrioritySeven {
  implicit def flattenerTo9[T1, T2, T3, T4, T5, T6, T7, T8, T9]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9) => (T1, T2, T3, T4, T5, T6, T7, T8, T9)] = TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9) => (T1, T2, T3, T4, T5, T6, T7, T8, T9)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 ~ t9) => (t1, t2, t3, t4, t5, t6, t7, t8, t9) }
}

trait PriorityNine extends PriorityEight {
  implicit def flattenerTo10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9 ~ T10) => (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] = TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9 ~ T10) => (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 ~ t9 ~ t10) => (t1, t2, t3, t4, t5, t6, t7, t8, t9, t10) }
}

object TupleFlattener extends PriorityNine {
  implicit def flattenerTo11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9 ~ T10 ~ T11) => (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)] = TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9 ~ T10 ~ T11) => (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 ~ t9 ~ t10 ~ t11) => (t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11) }
}

object CypherRow {
  def unapplySeq(row: CypherRow): Option[List[Any]] = Some(row.asList)
}

case class MetaDataItem(column: String, nullable: Boolean, clazz: String)

case class MetaData(ms: List[MetaDataItem]) {
  def get(columnName: String) = {
    val columnUpper = columnName.toUpperCase()
    dictionary.get(columnUpper)
  }

  private lazy val dictionary: Map[String, (String, Boolean, String)] =
    ms.map(m => (m.column.toUpperCase(), (m.column, m.nullable, m.clazz))).toMap

  lazy val columnCount = ms.size

  lazy val availableColumns: List[String] = ms.flatMap(i => List(i.column))
}

trait CypherRow {

  import scala.reflect.Manifest

  protected[anormcypher] val data: List[Any]
  protected[anormcypher] val metaData: MetaData

  lazy val asList = data.zip(metaData.ms.map(_.nullable)).map(i => if (i._2) Option(i._1) else i._1)

  lazy val asMap: scala.collection.Map[String, Any] = metaData.ms.map(_.column).zip(asList).toMap

  def get[A](a: String)(implicit c: Column[A]): MayErr[CypherRequestError, A] = CypherParser.get(a)(c)(this) match {
    case Success(a) => Right(a)
    case Error(e) => Left(e)
  }

  private def getType(t: String) = t match {
    case "long" => Class.forName("java.lang.Long")
    case "int" => Class.forName("java.lang.Integer")
    case "boolean" => Class.forName("java.lang.Boolean")
    case _ => Class.forName(t)
  }

  private lazy val ColumnsDictionary: Map[String, Any] = metaData.ms.map(_.column.toUpperCase()).zip(data).toMap
  private[anormcypher] def get1(a: String): MayErr[CypherRequestError, Any] = {
    for (
      meta <- metaData.get(a).toRight(ColumnNotFound(a, metaData.availableColumns));
      (column, nullable, clazz) = meta;
      result <- ColumnsDictionary.get(column.toUpperCase()).toRight(ColumnNotFound(column, metaData.availableColumns))
    ) yield result
  }

  def apply[B](a: String)(implicit c: Column[B]): B = get[B](a)(c).get

}

case class MockRow(metaData:MetaData, data: List[Any]) extends CypherRow

case class CypherResultRow(metaData:MetaData, data: List[Any]) extends CypherRow {
  override def toString() = "CypherResultRow(" + metaData.ms.zip(data).map(t => "'" + t._1.column + "':" + t._2 + " as " + t._1.clazz).mkString(", ") + ")"
}

object Useful {

  case class Var[T](var content: T)

  def drop[A](these: Var[Stream[A]], n: Int): Stream[A] = {
    var count = n
    while (!these.content.isEmpty && count > 0) {
      these.content = these.content.tail
      count -= 1
    }
    these.content
  }

  def unfold1[T, R](init: T)(f: T => Option[(R, T)]): (Stream[R], T) = f(init) match {
    case None => (Stream.Empty, init)
    case Some((r, v)) => (Stream.cons(r, unfold(v)(f)), v)
  }

  def unfold[T, R](init: T)(f: T => Option[(R, T)]): Stream[R] = f(init) match {
    case None => Stream.Empty
    case Some((r, v)) => Stream.cons(r, unfold(v)(f))
  }

}

case class CypherStatement(query:String, params:Map[String, Any] = Map()) {
  import Neo4jREST._
  import CypherParser._

  def apply() = sendQuery(this)

  def on(args:(String,Any) *) = this.copy(params=params ++ args)

  def execute(): Boolean = {
    var retVal = true
    try {
      // throws an exception on a query that doesn't succeed.
      sendQuery(this)
    } catch {
      case e:Exception => retVal = false
    }
    retVal
  }

  def as[T](parser: CypherResultSetParser[T]): T = {
    Cypher.as[T](parser, sendQuery(this))
  }

  def list[A](rowParser: CypherRowParser[A])(): Seq[A] = as(rowParser *)

  def single[A](rowParser: CypherRowParser[A])(): A = as(CypherResultSetParser.single(rowParser))

  def singleOpt[A](rowParser: CypherRowParser[A])(): Option[A] = as(CypherResultSetParser.singleOpt(rowParser))

  def parse[T](parser: CypherResultSetParser[T])(): T = Cypher.parse[T](parser, sendQuery(this))
}


object Cypher {

  import CypherParser._

  def apply(cypher:String) = CypherStatement(cypher)

  def as[T](parser: CypherResultSetParser[T], rs: Stream[CypherResultRow]): T =
    parser(rs) match {
      case Success(a) => a
      case Error(e) => sys.error(e.toString)
    }

  def parse[T](parser: CypherResultSetParser[T], rs: Stream[CypherResultRow]): T =
    parser(rs) match {
      case Success(a) => a
      case Error(e) => sys.error(e.toString)
    }

}
