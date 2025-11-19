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

// enum per lo stato del film rispetto all'utente
export enum MovieStatus {
  WATCHED = 'watched', // film già visto
  WATCHLIST = 'watchlist', // film da vedere
}

// entity per la relazione molti-a-molti tra utenti e film
// contiene tutti i dati specifici dell'utente per ogni film
@Entity('user_movies')
@Unique(['userId', 'movieId']) 
export class UserMovieEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @ManyToOne(() => UserEntity, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id' })
  user: UserEntity; // riferimento all'utente

  @Column({ name: 'movie_id', type: 'varchar', length: 255 })
  movieId: string;

  @ManyToOne(() => MovieEntity, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'movie_id' })
  movie: MovieEntity; // riferimento al film

  @Column({
    type: 'varchar',
    length: 20,
    enum: MovieStatus,
  })
  status: MovieStatus; // watched o watchlist

  @Column({ name: 'user_rating', type: 'decimal', precision: 3, scale: 1, nullable: true })
  userRating?: number; // voto personale 0-10

  @Column({ name: 'watched_date', type: 'date', nullable: true })
  watchedDate?: Date; // data in cui l'utente ha visto il film

  @Column({ name: 'user_review', type: 'text', nullable: true })
  userReview?: string; // recensione personale dell'utente

  @Column({ name: 'is_favorite', type: 'boolean', default: false })
  isFavorite?: boolean; // film preferito dell'utente

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date; // quando l'utente ha aggiunto il film

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date; // ultimo aggiornamento (rating, review, etc)
}