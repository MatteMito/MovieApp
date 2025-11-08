// entita database per relazione many-to-many tra utenti e film
// gestisce stato (watched/watchlist), rating personale, recensioni

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

// enum per stato film per utente
// watched: film visto
// watchlist: film da vedere
export enum MovieStatus {
  WATCHED = 'watched',
  WATCHLIST = 'watchlist',
}

// decoratore entity per typeorm
// tabella user_movies per relazione utente-film
// unique constraint su (userId, movieId) per evitare duplicati
@Entity('user_movies')
@Unique(['userId', 'movieId'])
export class UserMovieEntity {
  // id univoco della relazione
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // ===== relazioni foreign keys =====
  
  // foreign key verso tabella users
  // colonna user_id nel database
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  // relazione many-to-one con utente
  // onDelete: CASCADE = elimina relazioni se utente viene eliminato
  @ManyToOne(() => UserEntity, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id' })
  user: UserEntity;

  // foreign key verso tabella movies
  // colonna movie_id nel database
  @Column({ name: 'movie_id', type: 'varchar', length: 255 })
  movieId: string;

  // relazione many-to-one con film
  // onDelete: CASCADE = elimina relazioni se film viene eliminato
  @ManyToOne(() => MovieEntity, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'movie_id' })
  movie: MovieEntity;

  // ===== dati utente specifici =====
  
  // stato del film per questo utente (watched o watchlist)
  @Column({
    type: 'varchar',
    length: 20,
    enum: MovieStatus,
  })
  status: MovieStatus;

  // rating personale utente (0.0 - 10.0)
  // opzionale, solo per film watched
  @Column({ name: 'user_rating', type: 'decimal', precision: 3, scale: 1, nullable: true })
  userRating?: number;

  // data in cui l'utente ha visto il film
  // opzionale, per tracking cronologico
  @Column({ name: 'watched_date', type: 'date', nullable: true })
  watchedDate?: Date;

  // recensione testuale personale
  // opzionale, per note e commenti
  @Column({ name: 'user_review', type: 'text', nullable: true })
  userReview?: string;

  // flag film preferito
  // permette di marcare film speciali
  @Column({ name: 'is_favorite', type: 'boolean', default: false })
  isFavorite?: boolean;

  // ===== timestamp automatici =====
  
  // data aggiunta relazione (quando utente aggiunge film)
  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  // data ultimo aggiornamento (cambio status, rating, etc)
  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}