import cats._
import cats.implicits._
import com.monovore.decline._

object Demo {

  // Options for an imaginary Scala reimplementation of `tail`

  sealed trait Tail
  case class Run(lines: Int, follow: Boolean) extends Tail
  case object PrintVersion extends Tail

  val linesOpt: Opts[Int] =
    Opts.option[Int]("lines", help = "Number of lines to keep.").withDefault(10)

  val followOpt: Opts[Boolean] =
    Opts.flag("follow", help = "Continuously read from the file.").orFalse

  // These can be combined with applicative syntax

  val runOpts: Opts[Tail] = (linesOpt |@| followOpt).map(Run.apply)

  // Normal-style applicative, for reference

  trait MyApplicative[F[_]] extends Functor[F] {

    def pure[A](a: A): F[A]

    def ap[A, B](ff: F[A => B], fa: F[A]): F[B]
  }

  // 'Monoidal' applicative

  trait MonoidalApplicative[F[_]] extends Functor[F] {

    def unit: F[Unit]

    def product[A, B](fa: F[A], fb: F[B]): F[(A, B)]
  }

  // The regular / monoidal representations are equivalent

  def toMonoidal[F[_]](applicative: Applicative[F]): MonoidalApplicative[F] = new MonoidalApplicative[F] {

    override def unit: F[Unit] =
      applicative.pure(())

    override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
      applicative.ap(applicative.map(fb) { b: B => a: A => (a, b)})(fa)

    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      applicative.map(fa)(f)
  }

  def fromMonoidal[F[_]](monoidal: MonoidalApplicative[F]): Applicative[F] = ???

  // The laws for the monoidal version are easier:
  // product(unit, whatever) ~= whatever ~= product(whatever, unit)
  // product(a, product(b, c)) ~= product(product(b, c), a)

  // There's a second way to combine options, as 'alternatives'

  val versionOpt =
    Opts.flag("version", "Print the version and exit!").map { _ => PrintVersion }

  val tailOpts = runOpts orElse versionOpt

  // That gives us a _second_ monoid

  trait MonoidK[F[_]] {

    def empty[A]: F[A]

    def combine[A](x: F[A], y: F[A]): F[A]
  }

  runOpts <+> versionOpt

  // Alternative is just Applicative && MonoidK
}
