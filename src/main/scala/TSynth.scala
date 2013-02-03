import scalaz._
import scalaz.effect.ST
import shapeless.{HList, UnaryTCConstraint}
//import scalaz.effect.STArray

// TODO also have a system with free sinks. Possibly also explicit free sources. Use shapeless.HList?
// - hm seems like i'll need HList anyhow just for basic functionality
trait TBasicSystem[Sources <: HList, Sinks <: HList] {

  val sourceConstraint:UnaryTCConstraint[Sources, TSource]
  val sinkConstraint:UnaryTCConstraint[Sinks, TSink]

  def sources:Sources
  def sinks:Sinks

  //TODO deployment and a bunch of other stuff and thinking probably
  //should probably have a companion that constructs these, with checks that all sinks have source in sources
}

trait TNode {
  
  type TNodeImpl[S]

  def deploy[S]:ST[S, TNodeImpl[S]]
}

trait TSource[ElemType] extends TNode {

  type TNodeImpl[S] <: SourceImpl[S]
  type SourceImpl[S] <: TSourceImpl[S]

  trait TSourceImpl[S] {
    def value:ST[S,ElemType]
    def operate:ST[S,Unit]
  }
}

trait TSinkBase extends TNode {
  
  type TNodeImpl[S] <: SinkImpl[S]
  type SinkImpl[S] <: TSinkBaseImpl[S]

  trait TSinkBaseImpl[S] {
    def readSources:ST[S,Unit] // or just readSource assuming this is not a TMixer?
    def operate:ST[S,Unit] // should i care whether this comes from some common supertrait?
    // should i care whether, at this level, i should already have the source(s) themselves as members?
  }
}

trait TSink[ElemType] extends TSinkBase {

  type SinkImpl[S] <: TSinkImpl[S]

  def source:TSource[ElemType]
  //TODO think: can't i just categorically have a read-value variable where the source value gets put by
  //  readSources?
  trait TSinkImpl[S] extends TSinkBaseImpl[S] {
    def source:ST[S, TSink.source.TNodeImpl[S]]

    //def readSources = source.value // erm, what? no?
  }
}

trait TFilterBase[Out] extends TSource[Out] with TSinkBase {

  type TNodeImpl[S] <: SourceImpl[S] with SinkImpl[S]
  type SourceImpl[S] <: TFilterBaseImpl[S]
  type SinkImpl[S] <: TFilterBaseImpl[S]

  trait TFilterBaseImpl[S] extends TSourceImpl[S] with TSinkBaseImpl[S]
}

trait TFilter[In, Out] extends TSink[In] with TFilberBase[Out] {

  type SourceImpl[S] <: TFilterImpl[S]
  type SinkImpl[S] <: TFilterImpl[S]

  trait TFilterImpl[S] extends TSinkImpl[S] with TFilterBaseImpl[S]
}

trait TMixer2[In1, In2, Out] extends TFilterBase[Out] {

  type SourceImpl[S] <: TMixer2Impl[S]
  type SinkImpl[S] <: TMixer2Impl[S]

  def sources:(TSource[In1], TSource[In2])
  //def combine:(In1, In2) => Out // would of course only help with pairs of single samples.

  trait TMixer2Impl[S] extends TFilterBaseImpl[S] {
    def sources:ST[S, (sources._1.TNodeImpl[S], sources._2.TNodeImpl[S])]
    def combine:ST[S, Out]
  }
}

// First attempt!
//case class STToyDelayLine[S](buffer:STArray[S,Float], i:Int) { 
