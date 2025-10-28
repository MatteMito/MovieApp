import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('movie_lists')
export class MovieListEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id', type: 'uuid' })
  user_id: string;

  @Column({ type: 'varchar', length: 200 })
  name: string;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ name: 'movie_ids', type: 'text', array: true, default: '{}' })
  movie_ids: string[];

  @Column({ name: 'is_public', type: 'boolean', default: false })
  is_public: boolean;

  @Column({ name: 'follower_ids', type: 'text', array: true, default: '{}' })
  follower_ids: string[];

  @Column({ name: 'followers_count', type: 'int', default: 0 })
  followers_count: number;

  @Column({ name: 'target_date', type: 'date', nullable: true })
  target_date: Date;

  @Column({ type: 'varchar', length: 50, nullable: true })
  frequency: string;

  @CreateDateColumn({ name: 'created_at' })
  created_at: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updated_at: Date;
}