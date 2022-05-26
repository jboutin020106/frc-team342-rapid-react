// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import java.util.HashMap;
import java.util.Map;

import com.ctre.phoenix.ErrorCode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.SensorCollection;
import com.ctre.phoenix.motorcontrol.TalonFXSensorCollection;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.util.concurrent.Semaphore;
import edu.wpi.first.util.sendable.SendableBuilder;
import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Robot;

import static frc.robot.Constants.ClimbConstants.*;

public class ClimbSubsystem extends SubsystemBase {

  private WPI_TalonFX leftClimbLift;
  private WPI_TalonFX rightClimbLift;

  private TalonFXSensorCollection leftClimbLiftEncoder;
  private TalonFXSensorCollection rightClimbLiftEncoder;

  private SensorCollection pulse;

  private WPI_TalonSRX leadClimbRotate;
  private WPI_TalonSRX followClimbRotate;

  private AnalogInput climbLimitSwitch;

  private double position;
  private double currSpeed;

  // Used for locking the climber controls on the operator control before climb time
  private boolean climbMode;

  /** Creates a new ClimbSubsystem. */
  public ClimbSubsystem() {
    leftClimbLift = new WPI_TalonFX(LEFT_LIFT_MOTOR);
    rightClimbLift = new WPI_TalonFX(RIGHT_LIFT_MOTOR);

    leftClimbLiftEncoder = leftClimbLift.getSensorCollection();
    rightClimbLiftEncoder = rightClimbLift.getSensorCollection();

    leadClimbRotate = new WPI_TalonSRX(LEAD_ROTATE_MOTOR);
    followClimbRotate = new WPI_TalonSRX(FOLLOW_ROTATE_MOTOR);

    pulse = leadClimbRotate.getSensorCollection();

    climbLimitSwitch = new AnalogInput(CLIMB_LIMIT_SWITCH_PORT);

    followClimbRotate.follow(leadClimbRotate);

    if(Robot.checkType() == Robot.RobotType.A_BOT){
      followClimbRotate.setInverted(true);
    }
    
    // brake mode so that it does not fall when on the bars
    leftClimbLift.setNeutralMode(NeutralMode.Brake);
    rightClimbLift.setNeutralMode(NeutralMode.Brake);

    // locks or activates the climbers
    climbMode = false;
  }
  
  @Override
  public void periodic() {}

  /**
   * Moves the lift mechanism of the climber. <br>
   * Will not move outside of a window of encoder values for minimum and maximum positions.
   * 
   * @param speed the speed to move the climber at, [-1, 1]
   */
  public void liftClimb(double speed) {
    double leftPosition = leftClimbLiftEncoder.getIntegratedSensorPosition();
    double rightPosition = rightClimbLiftEncoder.getIntegratedSensorPosition();

    // only run if climb has been activated
    if (climbMode) {
      // above minimum position on each encoder since arms are not mechanically linked
      boolean leftAboveMin = (leftPosition >= LIFT_MIN_POSITION);
      boolean leftBelowMax = (leftPosition <= LIFT_MAX_POSITION);

      boolean rightAboveMin = (rightPosition >= LIFT_MIN_POSITION);
      boolean rightBelowMax = (rightPosition <= LIFT_MAX_POSITION);

      // only runs outside of bounds if it is moving back towards bounds
      if ((leftAboveMin || speed > 0) && (leftBelowMax || speed < 0)) {
        leftClimbLift.set(speed * CLIMB_SPEED);
      } else {
        // otherwise do not move
        leftClimbLift.set(0);
      }

      if ((rightAboveMin || speed > 0) && (rightBelowMax || speed < 0)) {
        rightClimbLift.set(speed * CLIMB_SPEED);
      } else {
        rightClimbLift.set(0);
      }
    } else {
      // do not run motors if climb mode is not enabled
      leftClimbLift.set(0);
      rightClimbLift.set(0);
    }
  }

  /**
   * Moves the rotational mechanism of the climber. 
   * Will not move outside of a window of angles for minimum and maximum position.
   *  
   * @param speed the speed to rotate at, [-1, 1]
   */
  public void rotateClimb(double speed) {
    
    boolean withinMinAngle = position > ROTATE_MIN_ANGLE;
    boolean minOrForward = withinMinAngle || speed > 0;
    boolean limitOverride = !getLimitState() || speed < 0;

    position = pulse.getPulseWidthPosition();
    currSpeed = speed;
    
    if(climbMode){  // If climbmode is enabled, arms are in max or moving back in, and limit switch is not triggered (or moving away from l.s)
      
      /**if(minOrForward){
        if(getLimitState()){
          if(speed > 0){
            leadClimbRotate.set(0);
          } else {
            leadClimbRotate.set(speed * CLIMB_SPEED);
          }
        } else {
          leadClimbRotate.set(speed * CLIMB_SPEED);
        }
      }
    }

    **/
      if(getLimitState())
      {
          if(position > ROTATE_MIN_ANGLE && position < ROTATE_MAX_ANGLE)
          {
              leadClimbRotate.set(speed * CLIMB_SPEED);
          }
      }
      else
      {
        leadClimbRotate.set(0);
      }
    }
  /**
   * Converts encoder ticks to degrees of motor rotation
   * 
   * @param ticks encoder ticks
   * @return degrees, continuous past 360
   */
  private double encoderTicksToDegrees(double ticks) {
    // 8192 encoder ticks in a rotation, 360 degrees in a rotation
    return ((ticks / ROTATE_ENCODER_TICKS_PER_ROT) * 360) * ROTATION_GEAR_RATIO;
    
  }

  /**
   * Stop the movement of the climb lift motors.
   */
  public void stopClimbLift() {
    leftClimbLift.set(0);
    rightClimbLift.set(0);
  }

  /**
   * Sets the position of both lift motor encoders back to 0.
   */
  public void resetLiftEncoders() {
    leftClimbLiftEncoder.setIntegratedSensorPosition(0, 0);
    rightClimbLiftEncoder.setIntegratedSensorPosition(0, 0);
  }

  /**
   * Sets the position of the rotate motor encoder to 0.
   */
  public void resetRotateEncoders() {
    leadClimbRotate.setSelectedSensorPosition(0);
  }

  /**
   * Stop the movement of the climb rotate motors.
   */
  public void stopClimbRotate() {
    leadClimbRotate.set(0);
  }

  /**
   * Changes the current status of climbMode to the desired state
   */
  public void setClimbMode(boolean mode) {
    climbMode = mode;
  }

  /**
   * Checks to see if limit switch was triggered. Compares current value of the analog input to 50 
   * When greater than 50, the limit switch has been pressed.
   * @return True when limit switch is triggered (Indicating climb has reached minimum angle). False otherwise.
   */
  public boolean getLimitState(){
    return (climbLimitSwitch.getValue() < 50);
  }

  /**
   * Returns climbMode
   * @return true if climb is active, false otherwise
   */
  public boolean getClimbMode() {
    return climbMode;
  }

  /**
   * Changes the current status of climbMode to the opposite state of what it was
   */
  public void toggleClimbMode() {
    climbMode = !climbMode;
  }

  @Override
  public void initSendable(SendableBuilder sendable) {
    sendable.setSmartDashboardType("ClimbSubsystem");
    sendable.addBooleanProperty("Climb mode", this::getClimbMode, this::setClimbMode);
    sendable.addDoubleProperty("Left lift encoder", leftClimbLiftEncoder::getIntegratedSensorPosition, null);
    sendable.addDoubleProperty("Right lift encoder", rightClimbLiftEncoder::getIntegratedSensorPosition, null);
    sendable.addDoubleProperty("Rotate encoder", () -> encoderTicksToDegrees(leadClimbRotate.getSelectedSensorPosition()), null);
    sendable.addBooleanProperty("Climb limit switch", this::getLimitState, null);
    sendable.addDoubleProperty("Current Rotate Speed", () -> currSpeed, null);
    sendable.addDoubleProperty("Position", () -> position, null);
    sendable.addDoubleProperty("Limit Switch Val", () -> climbLimitSwitch.getValue(), null);
  }

  /**
   * Test that each motor controller is connected.
   * 
   * @return a map of the motor's name and a boolean with true if it is connected
   */
  public Map<String, Boolean> test() {
    var motors = new HashMap<String, Boolean>();

    leftClimbLift.getBusVoltage();
    motors.put("Climb motor 1", leftClimbLift.getLastError() == ErrorCode.OK);

    rightClimbLift.getBusVoltage();
    motors.put("Climb motor 2", rightClimbLift.getLastError() == ErrorCode.OK);

    leadClimbRotate.getBusVoltage();
    motors.put("Second stage motor 1", leadClimbRotate.getLastError() == ErrorCode.OK);

    followClimbRotate.getBusVoltage();
    motors.put("Second stage motor 2", followClimbRotate.getLastError() == ErrorCode.OK);

    // encoder check
    motors.put("Climb rotation encoder", followClimbRotate.getSensorCollection().getPulseWidthRiseToFallUs() != 0);
    
    return motors;
  }
}
