package tsynth

import scalaz._
import scalaz.effect.ST
import shapeless.{HList, UnaryTCConstraint}
//import scalaz.effect.STArray

//TODO  i will need to figure out if and where and how i'll need laziness to facilitate cyclic references
//  - From what i gather, source/sources etc need to be declared lazy*, and the parameters for them need be
//    introduced under a different name as nullary function values ('by name?').
//   *- rather, i think, they need to _not be (regular) vals_?
//    - for i moment i had them as lazy vals already on the base traits but i'll keep them as defs for now 
//      because i don't want to restrict myself and i don't think this will ultimately cause any trouble

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
  
  type NodeImpl[S] <: TNodeImpl[S]

  def deploy[S]:ST[S, NodeImpl[S]]

  //TODO !!!! need to figure out how to responsibly [get data out of these]
  //      - so far, all the methods in these are non-ST-monadic! -- and, thankfully, private.
  trait TNodeImpl[S] {
    private[tsynth] def operate:Unit
  }
}

trait TSource[ElemType] extends TNode {

  type NodeImpl[S] <: TSourceImpl[S]

  trait TSourceImpl[S] extends TNodeImpl[S] {
    private[tsynth] def value:ElemType
  }
}

trait TSinkBase extends TNode {
  
  type NodeImpl[S] <: TSinkBaseImpl[S]

  trait TSinkBaseImpl[S] {

    private[tsynth] def readSources:Unit
    // should i care whether, at this level, i should already have the source(s) themselves as members?
  }
}

trait TSink[ElemType] extends TSinkBase {

  type NodeImpl[S] <: TSinkImpl[S]

  def source:TSource[ElemType]

  trait TSinkImpl[S] extends TSinkBaseImpl[S] {
    private[tsynth] def source:TSink.source.NodeImpl[S]
    private[tsynth] var lastRead:ElemType
    private[tsynth] def readSources:Unit { lastRead = source.value }
  }
}

trait TFilterBase[Out] extends TSource[Out] with TSinkBase {

  type NodeImpl[S] <: TFilterBaseImpl[S]

  trait TFilterBaseImpl[S] extends TSourceImpl[S] with TSinkBaseImpl[S]
}

trait TFilter[In, Out] extends TSink[In] with TFilberBase[Out] {

  type NodeImpl[S] <: TFilterImpl[S]

  trait TFilterImpl[S] extends TSinkImpl[S] with TFilterBaseImpl[S]
}

trait TMixer2[In1, In2, Out] extends TFilterBase[Out] {

  type NodeImpl[S] <: TMixer2Impl[S]

  def sources:(TSource[In1], TSource[In2])

  trait TMixer2Impl[S] extends TFilterBaseImpl[S] {
    private[tsynth] def sources:(sources._1.NodeImpl[S], sources._2.NodeImpl[S])
    private[tsynth] val lastRead:VarTuple2[In,Out]
    private[tsynth] def readSources:Unit {
      lastRead._1 = sources._1.value
      lastRead._2 = sources._2.value
    }
  }
}

case class VarTuple2[T1,T2](var _1:T1, var _2:T2)


//trait or abstract class or such?
//TODO keep remembering that this is an initial implementation and will need to be rethought!
//TODO this is actually not exactly what i want. in order to treat filter state separate from output values,
//      i need a separate 'value:ElemType' member that operate writes to. actually probably separate In and Out
//      types. i might ultimately want a completely different [backend] for delay lines, but that might do
//      at least for now, and possibly forever.
//      - actually looks like i'd even want an additional 'internal' type.
trait CircularArrayFilter[ElemType] extends TFilter[ElemType, ElemType] {

  type NodeImpl[S] <: CircularArrayFilterImpl[S]

  val length:Int //TODO think: does this even belong here. shouldn't i instead have a [Seq[?]] member?
                  //    - a seq or somesuch member with a length i can refer to.
  //TODO think, read: i might like some restriction on Array here to prevent user mutation of the array
  val headUpdate: (ElemType, Array[ElemType], Int, Int) => ElemType
  //TODO !!! think of how to facilitate [[variable lengths]] with 'extra length trick'

  trait CircularArrayFilterImpl[S] extends TFilterImpl[S] {
    private[tsynth] val arr:Array[ElemType]
    private[tsynth] var head = 0
    private[tsynth] var tail = length - 1

    //T0D0 given some [array-specific] function from a [subclass], operate should be definable already
    //  - Do I want the very initial array to be played as such at all? If not, how do I order the
    //    operations? -- hm -- if readSources are done first, would that even be avoidable at all?
    //    - Let it happen. It has more info anyway, so I can have things both ways, adding fixes of top if need

    private[tsynth] def operate {
      //TODO should i replace arr(head) here with value? if so, what do i update head with? do i need
      //    to split headUpdate into two separate functions with some different roles?, or just
      //    have another function in addition to the headUpdate as it exists already?
      arr(head) = headUpdate(lastRead, arr, head, tail)
      head = (head + 1) % length
      tail = (tail + 1) % length
    }
    private[tsynth] def value = arr(head)
  
    /*
    //T0d0 think:  what should the visibility on this be, if it's to be user-supplied? same question goes
    //      for other such things too, so go through them at some point!
    private[tsynth] def headUpdate( .... */
  }
}








