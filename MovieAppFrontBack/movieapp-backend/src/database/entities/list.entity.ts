import {
  Entity,
  Column,
  PrimaryGeneratedColumn,
  CreateDateColumn,
  UpdateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { UserEntity } from './user.entity';

@Entity('movie_lists')
@Index(['user_id'])
@Index(['is_public'])
@Index(['target_date'])
export class MovieListEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // PROPRIETARIO LISTA
  
  @Column()
  user_id: string;

  @ManyToOne(() => UserEntity, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id' })
  user: UserEntity;

  // DATI LISTA
  
  @Column({ length: 200 })
  name: string;

  @Column('text', { nullable: true })
  description?: string;

  @Column('simple-array', { default: () => "'{}'" })
  movie_ids: string[];

  // PIANIFICAZIONE (opzionale)
  
  @Column({ type: 'timestamp', nullable: true })
  target_date?: Date;

  @Column({ length: 50, nullable: true })
  frequency?: string;

  // FUNZIONALITÀ SOCIAL
  
  @Column({ default: false })
  is_public: boolean;

  @Column({ default: 0 })
  followers_count: number;

  @Column('simple-array', { default: () => "'{}'" })
  follower_ids: string[];
  
  @CreateDateColumn()
  created_at: Date;

  @UpdateDateColumn()
  updated_at: Date;
}