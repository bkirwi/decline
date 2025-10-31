package com.monovore.decline

/**
 * Controls which sections of the Help are rendered in its string representation
 */
class RenderOptions private (opts: Flags) {
  private def copy(f: Flags => Flags) = new RenderOptions(f(opts))

  /**
   * Toggle rendering of commands section
   *
   * @param enabled
   * @return
   */
  def withCommands(enabled: Boolean): RenderOptions = copy(_.copy(commands = enabled))

  /**
   * Toggle rendering of options section
   *
   * @param enabled
   * @return
   */
  def withOptions(enabled: Boolean): RenderOptions = copy(_.copy(options = enabled))

  /**
   * Toggle rendering of environment variables section
   *
   * @param enabled
   * @return
   */
  def withEnv(enabled: Boolean): RenderOptions = copy(_.copy(env = enabled))

  /**
   * Toggle rendering of usage section
   *
   * @param enabled
   * @return
   */
  def withUsage(enabled: Boolean): RenderOptions = copy(_.copy(usage = enabled))

  /**
   * Toggle rendering of errors section
   *
   * @param enabled
   * @return
   */
  def withErrors(enabled: Boolean): RenderOptions = copy(_.copy(errors = enabled))

  /**
   * Toggle rendering of description section
   *
   * @param enabled
   * @return
   */
  def withDescription(enabled: Boolean): RenderOptions = copy(_.copy(description = enabled))

  /**
   * Whether commands section should be rendered
   *
   * @return
   */
  def commandsEnabled: Boolean = opts.commands

  /**
   * Whether options section should be rendered
   *
   * @return
   */
  def optionsEnabled: Boolean = opts.options

  /**
   * Whether environment variables section should be rendered
   *
   * @return
   */
  def envEnabled: Boolean = opts.env

  /**
   * Whether usage section should be rendered
   *
   * @return
   */
  def usageEnabled: Boolean = opts.usage

  /**
   * Whether errors section should be rendered
   *
   * @return
   */
  def errorsEnabled: Boolean = opts.errors

  /**
   * Whether description section should be rendered
   *
   * @return
   */
  def descriptionEnabled: Boolean = opts.description
}

object RenderOptions {
  val default: RenderOptions = new RenderOptions(Flags())
  def apply(): RenderOptions = default
}

private[decline] case class Flags(
    commands: Boolean = true,
    options: Boolean = true,
    env: Boolean = true,
    usage: Boolean = true,
    errors: Boolean = true,
    description: Boolean = true
)
