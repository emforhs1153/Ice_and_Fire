package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.iceandfire.entity.ai.*;
import com.google.common.base.Predicate;
import net.ilexiconn.llibrary.server.animation.Animation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateClimber;
import net.minecraft.pathfinding.PathNavigateFlying;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

public class EntityMyrmexRoyal extends EntityMyrmexBase {

    private static final ResourceLocation TEXTURE_DESERT = new ResourceLocation("iceandfire:textures/models/myrmex/myrmex_desert_royal.png");
    private static final ResourceLocation TEXTURE_JUNGLE = new ResourceLocation("iceandfire:textures/models/myrmex/myrmex_jungle_royal.png");
    private static final DataParameter<Boolean> FLYING = EntityDataManager.<Boolean>createKey(EntityMyrmexRoyal.class, DataSerializers.BOOLEAN);
    public static final Animation ANIMATION_BITE = Animation.create(15);
    public static final Animation ANIMATION_STING = Animation.create(15);
    private int hiveTicks = 0;
    public int releaseTicks = 0;
    private int breedingTicks = 0;
    public float flyProgress;
    private boolean isFlying;
    private boolean isLandNavigator;
    private boolean isMating = false;
    public EntityMyrmexRoyal mate;

    public EntityMyrmexRoyal(World worldIn) {
        super(worldIn);
        this.setSize(1.9F, 1.86F);
        this.switchNavigator(true);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(FLYING, Boolean.valueOf(false));
    }

    private void switchNavigator(boolean onLand) {
        if (onLand) {
            this.moveHelper = new EntityMoveHelper(this);
            this.navigator = new PathNavigateClimber(this, world);
            this.isLandNavigator = true;
        } else {
            this.moveHelper = new EntityMyrmexRoyal.FlyMoveHelper(this);
            this.navigator = new PathNavigateFlying(this, world);
            this.isLandNavigator = false;
        }
    }

    public boolean isFlying() {
        if (world.isRemote) {
            return this.isFlying = this.dataManager.get(FLYING).booleanValue();
        }
        return isFlying;
    }

    public void setFlying(boolean flying) {
        this.dataManager.set(FLYING, flying);
        if (!world.isRemote) {
            this.isFlying = flying;
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("HiveTicks", hiveTicks);
        tag.setInteger("ReleaseTicks", releaseTicks);
        tag.setBoolean("Flying", this.isFlying());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        this.hiveTicks = tag.getInteger("HiveTicks");
        this.releaseTicks = tag.getInteger("ReleaseTicks");
        this.setFlying(tag.getBoolean("Flying"));
    }


    public void onLivingUpdate() {
        super.onLivingUpdate();
        boolean flying = this.isFlying() && !this.onGround;
        if (flying && flyProgress < 20.0F) {
            flyProgress += 1F;
        } else if (!flying && flyProgress > 0.0F) {
            flyProgress -= 1F;
        }
        if(flying){
            this.motionY += 0.08D;
        }
        if (flying && this.isLandNavigator) {
            switchNavigator(false);
        }
        if (!flying && !this.isLandNavigator) {
            switchNavigator(true);
        }
        if(flying && this.canSeeSky() && this.isBreedingSeason()){
            this.releaseTicks++;
        }
        if(!flying && this.canSeeSky() && this.isBreedingSeason() && this.getAttackTarget() == null && this.canMove() && this.onGround && !isMating){
            this.setFlying(true);
            this.motionY += 0.42D;
        }
        hiveTicks++;
        if (this.getAnimation() == ANIMATION_BITE && this.getAttackTarget() != null && this.getAnimationTick() == 6) {
            double dist = this.getDistanceSq(this.getAttackTarget());
            if (dist < 6) {
                this.getAttackTarget().attackEntityFrom(DamageSource.causeMobDamage(this), ((int) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue()));
            }
        }
        if (this.getAnimation() == ANIMATION_STING && this.getAttackTarget() != null && this.getAnimationTick() == 6) {
            double dist = this.getDistanceSq(this.getAttackTarget());
            if (dist < 6) {
                this.getAttackTarget().attackEntityFrom(DamageSource.causeMobDamage(this), ((int) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue() * 2));
                this.getAttackTarget().addPotionEffect(new PotionEffect(MobEffects.POISON, 70, 1));
            }
        }
        if(this.mate != null){
            this.world.setEntityState(this, (byte) 77);
            if(this.getDistance(this.mate) < 10){
                this.setFlying(false);
                this.mate.setFlying(false);
                isMating = true;
                if(this.onGround && this.mate.onGround){
                    breedingTicks++;
                    if(breedingTicks > 100) {
                        if (this.isEntityAlive()) {
                            this.mate.setDead();
                            this.setDead();
                            EntityMyrmexQueen queen = new EntityMyrmexQueen(this.world);
                            queen.copyLocationAndAnglesFrom(this);
                            queen.setJungleVariant(this.isJungle());
                            if(!world.isRemote){
                                world.spawnEntity(queen);
                            }
                        }
                        isMating = false;
                    }
                }
            }
            this.mate.mate = this;
            if(!this.mate.isEntityAlive()){
                this.mate.mate = null;
                this.mate = null;
            }
        }
    }

    protected void initEntityAI() {
        this.tasks.addTask(0, new MyrmexAIMoveToMate(this, 1.0D));
        this.tasks.addTask(1, new AIFlyRandom());
        this.tasks.addTask(2, new EntityAIAttackMelee(this, 1.0D, true));
        this.tasks.addTask(3, new MyrmexAILeaveHive(this, 1.0D));
        this.tasks.addTask(3, new MyrmexAIReEnterHive(this, 1.0D));
        this.tasks.addTask(4, new MyrmexAIMoveThroughHive(this, 1.0D));
        this.tasks.addTask(4, new MyrmexAIWanderHiveCenter(this, 1.0D));
        this.tasks.addTask(5, new MyrmexAIWander(this, 1D));
        this.tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        this.tasks.addTask(7, new EntityAILookIdle(this));
        this.targetTasks.addTask(1, new MyrmexAIDefendHive(this));
        this.targetTasks.addTask(2, new MyrmexAIFindMate(this));
        this.targetTasks.addTask(3, new EntityAIHurtByTarget(this, false, new Class[0]));
        this.targetTasks.addTask(4, new EntityAINearestAttackableTarget(this, EntityLiving.class, 10, true, true, new Predicate<EntityLiving>() {
            public boolean apply(@Nullable EntityLiving entity) {
                if(entity instanceof EntityMyrmexBase && EntityMyrmexRoyal.this.isBreedingSeason()){
                    return false;
                }
                return entity != null && !IMob.VISIBLE_MOB_SELECTOR.apply(entity) && !EntityMyrmexBase.haveSameHive(EntityMyrmexRoyal.this, entity);
            }
        }));

    }

    public boolean shouldMoveThroughHive(){
        return false;
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(50);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(9.0D);
    }

    @Override
    public ResourceLocation getAdultTexture() {
        return isJungle() ? TEXTURE_JUNGLE : TEXTURE_DESERT;
    }

    @Override
    public float getModelScale() {
        return 1.25F;
    }

    @Override
    public int getCasteImportance() {
        return 2;
    }

    public boolean shouldLeaveHive(){
        return isBreedingSeason();
    }

    public boolean shouldEnterHive(){
        return !isBreedingSeason();
    }

    @Override
    public boolean attackEntityAsMob(Entity entityIn) {
        if (this.getAnimation() != this.ANIMATION_STING && this.getAnimation() != this.ANIMATION_BITE) {
            this.setAnimation(this.getRNG().nextBoolean() ? this.ANIMATION_STING : this.ANIMATION_BITE);
            return true;
        }
        return false;
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_PUPA_WIGGLE, ANIMATION_BITE, ANIMATION_STING};
    }

    public boolean isBreedingSeason(){
        return true;//hiveTicks > 400;
    }

    @SideOnly(Side.CLIENT)
    public void handleStatusUpdate(byte id) {
        if (id == 76) {
            this.playEffect(20);
        } else if (id == 77) {
            this.playEffect(7);
        } else {
            super.handleStatusUpdate(id);
        }
    }

    protected void playEffect(int hearts) {
        EnumParticleTypes enumparticletypes = EnumParticleTypes.HEART;

        for (int i = 0; i < hearts; ++i) {
            double d0 = this.rand.nextGaussian() * 0.02D;
            double d1 = this.rand.nextGaussian() * 0.02D;
            double d2 = this.rand.nextGaussian() * 0.02D;
            this.world.spawnParticle(enumparticletypes, this.posX + (double) (this.rand.nextFloat() * this.width * 2.0F) - (double) this.width, this.posY + 0.5D + (double) (this.rand.nextFloat() * this.height), this.posZ + (double) (this.rand.nextFloat() * this.width * 2.0F) - (double) this.width, d0, d1, d2);
        }
    }

    public static BlockPos getPositionRelativetoGround(Entity entity, World world, double x, double z, Random rand) {
        BlockPos pos = new BlockPos(x, entity.posY, z);
        for (int yDown = 0; yDown < 10; yDown++) {
            if (!world.isAirBlock(pos.down(yDown))) {
                return pos.up(yDown);
            }
        }
        return pos;
    }

    class FlyMoveHelper extends EntityMoveHelper {
        public FlyMoveHelper(EntityMyrmexRoyal pixie) {
            super(pixie);
            this.speed = 1.75F;
        }

        public void onUpdateMoveHelper() {
            if (this.action == EntityMoveHelper.Action.MOVE_TO) {
                double d0 = this.posX - EntityMyrmexRoyal.this.posX;
                double d1 = this.posY - EntityMyrmexRoyal.this.posY;
                double d2 = this.posZ - EntityMyrmexRoyal.this.posZ;
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                d3 = (double) MathHelper.sqrt(d3);

                if (d3 < EntityMyrmexRoyal.this.getEntityBoundingBox().getAverageEdgeLength()) {
                    this.action = EntityMoveHelper.Action.WAIT;
                    EntityMyrmexRoyal.this.motionX *= 0.5D;
                    EntityMyrmexRoyal.this.motionY *= 0.5D;
                    EntityMyrmexRoyal.this.motionZ *= 0.5D;
                } else {
                    EntityMyrmexRoyal.this.motionX += d0 / d3 * 0.15D * this.speed;
                    EntityMyrmexRoyal.this.motionY += d1 / d3 * 0.15D * this.speed;
                    EntityMyrmexRoyal.this.motionZ += d2 / d3 * 0.15D * this.speed;

                    if (EntityMyrmexRoyal.this.getAttackTarget() == null) {
                        EntityMyrmexRoyal.this.rotationYaw = -((float) MathHelper.atan2(EntityMyrmexRoyal.this.motionX, EntityMyrmexRoyal.this.motionZ)) * (180F / (float) Math.PI);
                        EntityMyrmexRoyal.this.renderYawOffset = EntityMyrmexRoyal.this.rotationYaw;
                    } else {
                        double d4 = EntityMyrmexRoyal.this.getAttackTarget().posX - EntityMyrmexRoyal.this.posX;
                        double d5 = EntityMyrmexRoyal.this.getAttackTarget().posZ - EntityMyrmexRoyal.this.posZ;
                        EntityMyrmexRoyal.this.rotationYaw = -((float) MathHelper.atan2(d4, d5)) * (180F / (float) Math.PI);
                        EntityMyrmexRoyal.this.renderYawOffset = EntityMyrmexRoyal.this.rotationYaw;
                    }
                }
            }
        }
    }

    class AIFlyRandom extends EntityAIBase {
        BlockPos target;

        public AIFlyRandom() {
            this.setMutexBits(1);
        }

        public boolean shouldExecute() {
            if(EntityMyrmexRoyal.this.isFlying()) {
                target = EntityMyrmexRoyal.getPositionRelativetoGround(EntityMyrmexRoyal.this, EntityMyrmexRoyal.this.world, EntityMyrmexRoyal.this.posX + EntityMyrmexRoyal.this.rand.nextInt(30) - 15, EntityMyrmexRoyal.this.posZ + EntityMyrmexRoyal.this.rand.nextInt(30) - 15, EntityMyrmexRoyal.this.rand);
                return isDirectPathBetweenPoints(EntityMyrmexRoyal.this.getPosition(), target) && !EntityMyrmexRoyal.this.getMoveHelper().isUpdating() && EntityMyrmexRoyal.this.rand.nextInt(2) == 0;
            }else{
                return false;
            }
        }

        protected boolean isDirectPathBetweenPoints(BlockPos posVec31, BlockPos posVec32) {
            RayTraceResult raytraceresult = EntityMyrmexRoyal.this.world.rayTraceBlocks(new Vec3d(posVec31.getX() + 0.5D, posVec31.getY() + 0.5D, posVec31.getZ() + 0.5D), new Vec3d(posVec32.getX() + 0.5D, posVec32.getY() + (double) EntityMyrmexRoyal.this.height * 0.5D, posVec32.getZ() + 0.5D), false, true, false);
            return raytraceresult == null || raytraceresult.typeOfHit == RayTraceResult.Type.MISS;
        }

        public boolean shouldContinueExecuting() {
            return false;
        }

        public void updateTask() {
            if (!isDirectPathBetweenPoints(EntityMyrmexRoyal.this.getPosition(), target)) {
                target = EntityMyrmexRoyal.getPositionRelativetoGround(EntityMyrmexRoyal.this, EntityMyrmexRoyal.this.world, EntityMyrmexRoyal.this.posX + EntityMyrmexRoyal.this.rand.nextInt(15) - 7, EntityMyrmexRoyal.this.posZ + EntityMyrmexRoyal.this.rand.nextInt(15) - 7, EntityMyrmexRoyal.this.rand);
            }
            if (EntityMyrmexRoyal.this.world.isAirBlock(target)) {
                EntityMyrmexRoyal.this.moveHelper.setMoveTo((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 0.25D);
                if (EntityMyrmexRoyal.this.getAttackTarget() == null) {
                    EntityMyrmexRoyal.this.getLookHelper().setLookPosition((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 180.0F, 20.0F);

                }
            }
        }
    }
}
