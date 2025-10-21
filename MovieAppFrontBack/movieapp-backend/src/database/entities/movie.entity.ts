import {
  Entity,
  Column,
  PrimaryColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

@Entity('movies')
@Index(['title'])
@Index(['year'])
@Index(['director'])
@Index(['tmdb_id'])
@Index(['is_watched'])
@Index(['source'])
export class MovieEntity {
  @PrimaryColumn()
  id: string;

  @Column({ length: 500 })
  title: string;

  @Column({ nullable: true })
  year?: number;

  @Column({ nullable: true, length: 255 })
  director?: string;

  @Column('text', { array: true, default: [] })
  genres: string[];

  @Column('text', { array: true, default: [] })
  cast: string[];

  @Column('text', { nullable: true })
  overview?: string;

  @Column({ nullable: true, length: 500 })
  tagline?: string;

  @Column({ nullable: true })
  runtime?: number;

  @Column('decimal', { precision: 3, scale: 1, nullable: true })
  user_rating?: number;

  @Column({ nullable: true })
  date_rated?: string;

  @Column({ nullable: true, length: 100 })
  watched_date?: string;

  @Column('text', { nullable: true })
  user_review?: string;

  @Column({ default: false })
  is_watched: boolean;

  @Column({ default: false })
  is_enriched: boolean;

  @Column({ length: 50 })
  source: string;

  @Column({ nullable: true })
  tmdb_id?: number;

  @Column({ nullable: true, length: 500 })
  poster_url?: string;

  @Column({ nullable: true, length: 500 })
  backdrop_url?: string;

  @Column('decimal', { precision: 3, scale: 1, nullable: true })
  tmdb_rating?: number;

  @Column({ nullable: true })
  vote_count?: number;

  @Column('bigint', { nullable: true })
  budget?: number;

  @Column('bigint', { nullable: true })
  revenue?: number;

  @Column({ nullable: true, length: 100 })
  status?: string;

  @Column({ nullable: true, length: 10 })
  original_language?: string;

  @Column({ nullable: true, length: 500 })
  original_title?: string;

  @Column('decimal', { precision: 10, scale: 3, nullable: true })
  popularity?: number;

  @Column({ default: false })
  adult: boolean;

  @Column({ nullable: true, length: 500 })
  homepage?: string;

  @Column({ nullable: true, length: 20 })
  imdb_id?: string;

  @Column('text', { array: true, default: [] })
  production_companies: string[];

  @Column('text', { array: true, default: [] })
  production_countries: string[];

  @Column('text', { array: true, default: [] })
  spoken_languages: string[];

  @Column('text', { array: true, default: [] })
  keywords: string[];

  @Column({ nullable: true, length: 20 })
  certification?: string;

  @Column({ nullable: true, length: 500 })
  trailer_url?: string;

  @CreateDateColumn()
  created_at: Date;

  @UpdateDateColumn()
  updated_at: Date;

  // GETTER PER COMPATIBILITÀ
  get isWatched(): boolean {
    return this.is_watched;
  }
}