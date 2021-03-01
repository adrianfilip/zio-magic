package zio.magic.macros.utils

import zio.magic.macros.graph.Node
import zio.{Has, ZLayer}

import scala.reflect.macros.blackbox

trait MacroUtils {
  val c: blackbox.Context
  import c.universe._

  private val zioSymbol = typeOf[Has[_]].typeSymbol

  type LayerExpr = c.Expr[ZLayer[_, _, _]]

  def getNode(layer: LayerExpr): Node[LayerExpr] = {
    val tpe                   = layer.actualType.dealias
    val in :: _ :: out :: Nil = tpe.typeArgs
    Node(getRequirements(in), getRequirements(out), layer)
  }

  def getRequirements[T: c.WeakTypeTag]: List[String] =
    getRequirements(weakTypeOf[T])

  def getRequirements(tpe: Type): List[String] =
    tpe.intersectionTypes
      .filter(_.dealias.typeSymbol == zioSymbol)
      .map(_.dealias.typeArgs.head.dealias.map(_.dealias).toString)
      .distinct

  def assertProperVarArgs(layers: Seq[c.Expr[_]]): Unit =
    layers.map(_.tree) collect { case Typed(_, Ident(typeNames.WILDCARD_STAR)) =>
      c.abort(
        c.enclosingPosition,
        "Magic doesn't work with `someList: _*` syntax.\nPlease pass the layers themselves into this function."
      )
    }

  def assertEnvIsNotNothing[Out <: Has[_]: c.WeakTypeTag](): Unit = {
    val outType     = weakTypeOf[Out]
    val nothingType = weakTypeOf[Nothing]
    if (outType == nothingType) {
      val fromMagicName  = fansi.Bold.On("fromMagic")
      val typeAnnotation = fansi.Color.White("[A with B]")
      val errorMessage =
        s"""
           |🪄  You must provide a type to $fromMagicName (e.g. ZIO.fromMagic$typeAnnotation(A.live, B.live))
           |""".stripMargin
      c.abort(c.enclosingPosition, errorMessage)
    }
  }

  implicit class TypeOps(self: Type) {

    /** Given a type `A with B with C` You'll get back List[A,B,C]
      */
    def intersectionTypes: List[Type] = self.dealias match {
      case t: RefinedType =>
        t.parents.flatMap(_.dealias.intersectionTypes)
      case _ => List(self)
    }
  }

  implicit class ZLayerExprOps(self: c.Expr[ZLayer[_, _, _]]) {
    def outputTypes: List[Type] = self.actualType.dealias.typeArgs(2).intersectionTypes
    def inputTypes: List[Type]  = self.actualType.dealias.typeArgs.head.intersectionTypes
  }

  implicit class TreeOps(self: c.Expr[_]) {
    def showTree: String = CleanCodePrinter.show(c)(self.tree)
  }
}
