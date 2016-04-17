package Models;

import Abilities.Ability;
import Abilities.MeleeAttack;
import Enumerations.Abilities;
import Enumerations.AbilityState;
import Enumerations.DamageType;
import Enumerations.EntityState;
import Game.Main;
import Interfaces.IMovable;
import Renderer.Animation;
import World.Coord;
import World.Physics;

import java.util.EnumSet;
import java.util.HashMap;

abstract public class Creature extends Entity implements IMovable{
    // Stats
    private int healthPoints;
    private int attackPower;
    private int armorValue;
    HashMap<Abilities, Ability> abilities;
    double immuneTime = 0; // time ot next damage instance

    // Physical characteristics
    private double radius; // used for collision detection
    private double maxSpeed;
    private double maxAcceleration;
    private Coord velocity; // current velocity vector

    public Creature(Animation animation,
                    double x, double y,
                    double direction, int healthPoints,
                    int attackPower, int armorValue,
                    HashMap<Abilities, Ability> abilities,
                    double radius, double maxSpeed,
                    double maxAcceleration) {
        super(animation, x, y, direction, radius);
        this.healthPoints = healthPoints;
        this.attackPower = attackPower;
        this.armorValue = armorValue;
        this.abilities = abilities;
        this.radius = radius;
        this.maxSpeed = maxSpeed;
        this.maxAcceleration = maxAcceleration;
        velocity = new Coord(0.0, 0.0);
    }

    // Properties
    public int getHealthPoints() {
        return healthPoints;
    }
    public void setHealthPoints(int value) {
        this.healthPoints = value;
    }
    public int getAttackPower() {
        return attackPower;
    }
    public void setAttackPower(int value) {
        this.attackPower = value;
    }
    public int getArmorValue() {
        return armorValue;
    }
    public void setArmorValue(int value) {
        this.armorValue = value;
    }

    // Implementation of IMovable, everything to do with motion
    @Override
    public void accelerate(Coord vector, double time) {
        vector.scale(time);
        velocity.doAdd(vector);
        if (velocity.getMagnitude() > Physics.maxVelocity) {
            velocity.setMagnitude(Physics.maxVelocity);
        }
    }

    @Override
    public void stop() {
        velocity = new Coord(0, 0);
    }

    @Override
    public void place(Coord newPosition) {
        super.setPos(newPosition);
    }

    @Override
    public void place(double newX, double newY) {
        super.setPos(newX, newY);
    }

    @Override
    public boolean hitscan(Entity target) {
        // TODO: Implement collision detection
        Coord dist = new Coord(getX(), getY());
        dist.doSubtract(target.getPos());
        double penetration = dist.getMagnitude() - (radius + target.getRadius()); // TODO: Replace this with entity size
        if (penetration < 0.0) {
            // collision; resolve via projection (entities placed apart, no vector modification)
            Main.debugInfo += String.format("%ncollision");
            dist.setMagnitude(penetration / 2); // separation vector
            getPos().doSubtract(dist);
            dist.scale(-1); // push target entity in opposite direction
            target.getPos().doSubtract(dist);
            // TODO: modify each entity's velocity vector, so they aren't moving towards each other
            return true;
        }
        return false;
    }

    @Override
    public Coord getVelocity() {
        return velocity;
    }

    @Override
    public void setVelocity(Coord newVelocity) {
        velocity = newVelocity;
    }

    /**
     * Since many operation require the creature to just move where it's looking, this method takes care of all vector
     * calc and just adds the needed acceleration in the proper direction
     * @param time seconds since last update
     */
    public void moveForward(double time) {
        // TODO: extend checks for staggering effects that prevent voluntary movement
        if (velocity.getMagnitude() > Physics.maxVelocity) return; // this checks for sudden movements (like knockback)
        Coord vector = new Coord(maxAcceleration + Physics.friction, 0.0);
        vector.setDirection(getDirection());
        accelerate(vector, time);
        if (velocity.getMagnitude() > maxSpeed) velocity.setMagnitude(maxSpeed); // make sure we're not going too fast
    }

    /**
     * Update the creature, depending on time elapsed since last update. Process behaviour, if entity has an AI
     * attached, look for collisions with other objects, move physically, cool down all used abilites, etc.
     * @param time Seconds since last update
     */
    public void update(double time) {
        // Keep track of damage instances
        if (immuneTime > 0) immuneTime -= time;
        if (getState().contains(EntityState.DAMAGED) && immuneTime <= 0) {
            getState().remove(EntityState.DAMAGED);
            getState().remove(EntityState.STAGGERED);
            immuneTime = 0;
        }
        // Process behaviour
        if (this instanceof Enemy) {
            ((Enemy)this).processBehaviour(time);
        }

        // Detect collisions
        // TODO: this will check each pair twice, make a separate list and deplete it
        // TODO: wall detection
        Main.game.getLevel().getEntities().stream()
                .filter(entity -> !entity.hasState(EntityState.DEAD)) // don't collide with corpses
                .filter(entity -> entity instanceof Creature) // get just the creatures
                .filter(entity -> !entity.equals(this)) // can't collide with self
                .forEach(entity -> ((Creature)entity).hitscan(this)); // resolution currently included in detection, can be filtered further

        // If the object is moving, apply friction
        if (velocity.getMagnitude() != 0) Physics.decelerate(velocity, time);
        double newX = super.getX() + velocity.getX() * time;
        double newY = super.getY() + velocity.getY() * time;
        super.setX(newX);
        super.setY(newY);

        // Update used abilities (they cool themselves down)
        abilities.entrySet().stream()
                .filter(entry -> !entry.getValue().isReady()) // Filter used abilities
                .forEach(entry -> entry.getValue().update(time));
    }

    // Abilities

    /**
     * Add an ability to the list
     * @param name A name from the dedicated enumeration
     * @param ability An instance of the ability. Do not attach the same reference to two different creatures, since
     *                cooldown and effects are linked back to the creature
     */
    public void addAbility(Abilities name, Ability ability) {
        abilities.put(name, ability);
    }

    public void useAbility(Abilities ability) {
        if (!isReady()) return;
        if (abilities.containsKey(ability)) {
            if (!abilities.get(ability).isReady()) return;
            stopAbilities(); // cancel all ongoing abilities
            abilities.get(ability).use();
        }
    }

    // Put all abilities that are processing into cooldown
    public void stopAbilities() {
        abilities.entrySet().stream()
                .filter(entry -> entry.getValue().getState() == AbilityState.INIT ||
                        entry.getValue().getState() == AbilityState.RECOVER)
                .forEach(entry -> entry.getValue().spend());
    }

    public void takeDamage(double damage) {
        resolveDamage(damage, DamageType.GENERIC, null);
    }
    public void takeDamage(double damage, DamageType type) {
        resolveDamage(damage, type, null);
    }
    public void takeDamage(double damage, Coord source) {
        resolveDamage(damage, DamageType.GENERIC, source);
    }
    public void takeDamage(double damage, DamageType type, Coord source) {
        resolveDamage(damage, type, source);
    }

    public void resolveDamage(double damage, DamageType type, Coord source) {
        // TODO: armor calculation
        if ((type == DamageType.WEAPONMELEE || type == DamageType.WEAPONRANGED) && source != null) {
            // knockback
            double refsize = 0.25; // temp scalar
            double knockback = 2 - 2 * (radius - 0.5 * refsize) / (1.5 * refsize);
            if (knockback > 2) knockback = 2;
            if (knockback > 0) { // don't do anything if entity is not affected
                Coord kickvector = new Coord(knockback * 5, 0.0);
                kickvector.setDirection(Coord.angleBetween(source, getPos()));
                stop();
                accelerate(kickvector, 1.0);
                // TODO: disable movement for a short period
            }
        }
        // TODO: stagger for enemies, player
        if (damage > 0) { // prevent negative damage from healing
            if (getState().contains(EntityState.DAMAGED)) return; // prevent instances from resolving more than once
            getState().add(EntityState.DAMAGED);
            if (this instanceof Player) {
                setState(EnumSet.of(EntityState.STAGGERED)); // player always gets staggered
                immuneTime = 0.5;
            } else {
                immuneTime = 0.1; // Enemies have a much shorter invinciframe
            }

            healthPoints -= (int)damage;

            // todo add dying animation
            if (healthPoints <= 0) {
                stopAbilities();
                setState(EnumSet.of(EntityState.DEAD));
            }
        }
    }
}
