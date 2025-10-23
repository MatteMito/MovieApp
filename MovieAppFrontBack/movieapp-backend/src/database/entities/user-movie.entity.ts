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
@Unique(['userId', 'movieId']) // Un utente può avere un film una sola volta
export class UserMovieEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // Relazioni
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

  // Stato del film per l'utente
  @Column({
    type: 'varchar',
    length: 20,
    enum: MovieStatus,
  })
  status: MovieStatus;

  // Dati utente specifici
  @Column({ name: 'user_rating', type: 'decimal', precision: 3, scale: 1, nullable: true })
  userRating?: number;

  @Column({ name: 'watched_date', type: 'timestamp', nullable: true })
  watchedDate?: Date;

  @Column({ name: 'user_review', type: 'text', nullable: true })
  userReview?: string;

  // Metadati
  @Column({ type: 'varchar', length: 50, default: 'UNKNOWN' })
  source: string; // IMDB, Letterboxd, Manual, etc.

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}