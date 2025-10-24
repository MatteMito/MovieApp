import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Unique,
} from 'typeorm';
import { UserEntity } from './user.entity';
import { MovieEntity } from './movie.entity';

export enum MovieStatus {
  WATCHED = 'watched',
  WATCHLIST = 'watchlist',
}

@Entity('user_movies')
@Unique(['userId', 'movieId'])
export class UserMovieEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @ManyToOne(() => UserEntity, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id' })
  user: UserEntity;

  @Column({ name: 'movie_id', type: 'varchar', length: 255 })
  movieId: string;

  @ManyToOne(() => MovieEntity, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'movie_id' })
  movie: MovieEntity;

  @Column({
    type: 'varchar',
    length: 20,
    enum: MovieStatus,
  })
  status: MovieStatus;

  @Column({ name: 'user_rating', type: 'decimal', precision: 3, scale: 1, nullable: true })
  userRating?: number;

  @Column({ name: 'watched_date', type: 'date', nullable: true })
  watchedDate?: Date;

  @Column({ name: 'user_review', type: 'text', nullable: true })
  userReview?: string;

  @Column({ name: 'is_favorite', type: 'boolean', default: false })
  isFavorite?: boolean;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}